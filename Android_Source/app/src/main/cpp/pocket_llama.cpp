#include <jni.h>
#include "llama.h"
#include "chat.h"
#include "ggml-backend.h"
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <codecvt>
#include <locale>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
struct Model {
    llama_model * model=nullptr;
    llama_context * ctx=nullptr;
    common_chat_templates_ptr templates;
    std::vector<llama_token> decoded;
    ~Model() { if(ctx)llama_free(ctx);if(model)llama_model_free(model); }
};
std::string utf8(JNIEnv * env,jstring str) {
    const auto * chars=env->GetStringChars(str,nullptr);
    if(!chars)throw std::runtime_error("Cannot read text");
    std::u16string text(reinterpret_cast<const char16_t *>(chars),env->GetStringLength(str));
    env->ReleaseStringChars(str,chars);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>,char16_t>{}.to_bytes(text);
}
void error(JNIEnv * env,const std::exception & e) {
    if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());
}
struct LoadProgress {
    JavaVM * vm;
    jobject callback;
    jmethodID method;
    static bool report(float value,void * ptr) {
        auto & a=*static_cast<LoadProgress *>(ptr);
        JNIEnv * env=nullptr;
        bool attached=a.vm->GetEnv(reinterpret_cast<void **>(&env),JNI_VERSION_1_6)==JNI_EDETACHED;
        if(attached && a.vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return false;
        bool proceed=env->CallBooleanMethod(a.callback,a.method,value) && !env->ExceptionCheck();
        if(attached)a.vm->DetachCurrentThread();
        return proceed;
    }
};
struct Abort {
    JavaVM * vm;
    jobject callback;
    jmethodID cancelled;
    std::chrono::steady_clock::time_point deadline=std::chrono::steady_clock::now()+std::chrono::seconds(180);
    static bool check(void * ptr) {
        auto & a=*static_cast<Abort *>(ptr);
        if(std::chrono::steady_clock::now()>a.deadline)return true;
        JNIEnv * env=nullptr;
        bool attached=a.vm->GetEnv(reinterpret_cast<void **>(&env),JNI_VERSION_1_6)==JNI_EDETACHED;
        if(attached && a.vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return true;
        bool stop=env->CallBooleanMethod(a.callback,a.cancelled) || env->ExceptionCheck();
        if(attached)a.vm->DetachCurrentThread();
        return stop;
    }
};
// Hold an incomplete UTF-8 character until the next token arrives.
size_t complete_utf8(const std::string & s) {
    size_t i=0;
    while(i<s.size()) {
        unsigned char c=s[i];
        size_t n=c<0x80?1:(c&0xe0)==0xc0?2:(c&0xf0)==0xe0?3:(c&0xf8)==0xf0?4:1;
        if(i+n>s.size())break;
        i+=n;
    }
    return i;
}
}

extern "C" JNIEXPORT jlong JNICALL Java_jp_stackchan_pocket_GgufLlm_nativeLoad(JNIEnv * env,jobject,jstring path,jint threads,jstring libraryDir,jboolean gpu,jobject callback) {
    jobject global=env->NewGlobalRef(callback);
    struct Ref { JNIEnv * env;jobject obj;~Ref(){if(obj)env->DeleteGlobalRef(obj);} } ref{env,global};
    try {
        static std::once_flag init;
        auto directory=utf8(env,libraryDir);
        std::call_once(init,[&]{
            llama_log_set([](ggml_log_level level,const char * text,void *) {
                std::string line(text);
                if(level==GGML_LOG_LEVEL_WARN || level==GGML_LOG_LEVEL_ERROR || line.find("offload")!=std::string::npos || line.find("buffer size")!=std::string::npos)
                    __android_log_print(level==GGML_LOG_LEVEL_ERROR?ANDROID_LOG_ERROR:ANDROID_LOG_INFO,"PocketNative","%s",text);
            },nullptr);
            ggml_backend_load_all_from_path(directory.c_str());
            if(!ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_CPU))
                throw std::runtime_error("No compatible GGUF CPU backend was packaged");
            llama_backend_init();
            __android_log_print(ANDROID_LOG_INFO,"PocketGGUF","CPU features: %s",llama_print_system_info());
        });
        auto m=std::make_unique<Model>();
        ggml_backend_dev_t devices[2]={nullptr,nullptr};
        if(gpu) {
            auto reg=ggml_backend_reg_by_name("Vulkan");
            if(!reg || !ggml_backend_reg_dev_count(reg))throw std::runtime_error("Vulkan GPU unavailable; select CPU and reload");
            devices[0]=ggml_backend_reg_dev_get(reg,0);
            __android_log_print(ANDROID_LOG_INFO,"PocketGGUF","GPU: %s",ggml_backend_dev_description(devices[0]));
        }
        JavaVM * vm;env->GetJavaVM(&vm);
        if(!global)throw std::runtime_error("Missing load callback");
        auto cls=env->GetObjectClass(global);
        LoadProgress progress{vm,global,env->GetMethodID(cls,"progress","(F)Z")};
        if(!progress.method)throw std::runtime_error("Missing load progress method");
        auto params=llama_model_default_params();params.devices=devices;
        params.progress_callback=LoadProgress::report;params.progress_callback_user_data=&progress;
        params.n_gpu_layers=gpu?99:0;params.load_mode=LLAMA_LOAD_MODE_MMAP;
        m->model=llama_model_load_from_file(utf8(env,path).c_str(),params);
        if(!m->model)throw std::runtime_error("GGUF load failed: check architecture, quantization and available RAM");
        if(llama_model_has_encoder(m->model))throw std::runtime_error("Only text decoder GGUF models are supported");
        if(!llama_model_chat_template(m->model,nullptr))throw std::runtime_error("GGUF has no chat template; use an instruction/chat model");
        m->templates=common_chat_templates_init(m->model,"");
        if(!LoadProgress::report(-1,&progress))throw std::runtime_error("Model load cancelled");
        auto cp=llama_context_default_params();cp.n_ctx=4096;cp.n_batch=128;cp.n_ubatch=128;
        cp.n_threads=threads;cp.n_threads_batch=threads;
        cp.offload_kqv=gpu;cp.op_offload=gpu;
        m->ctx=llama_init_from_model(m->model,cp);
        if(!m->ctx)throw std::runtime_error("GGUF context allocation failed: insufficient RAM or unsupported model");
        return reinterpret_cast<jlong>(m.release());
    }catch(const std::exception & e){error(env,e);return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_jp_stackchan_pocket_GgufLlm_nativeGenerate(JNIEnv * env,jobject,jlong handle,jobjectArray roles,jobjectArray contents,jint maxTokens,jboolean reuse,jint seed,jobject callback) {
    auto * m=reinterpret_cast<Model *>(handle);
    jobject global=env->NewGlobalRef(callback);
    try {
        if(!m || !global)throw std::runtime_error("GGUF engine is unavailable");
        JavaVM * vm;env->GetJavaVM(&vm);
        auto cls=env->GetObjectClass(callback);
        Abort abort{vm,global,env->GetMethodID(cls,"cancelled","()Z")};
        auto tokenMethod=env->GetMethodID(cls,"token","([B)V");
        if(!abort.cancelled || !tokenMethod)throw std::runtime_error("Missing generation callback");
        llama_set_abort_callback(m->ctx,Abort::check,&abort);
        struct ResetAbort { llama_context * ctx;~ResetAbort(){llama_set_abort_callback(ctx,nullptr,nullptr);} } reset{m->ctx};
        common_chat_templates_inputs input;input.enable_thinking=false;input.force_pure_content=true;
        if(env->GetArrayLength(roles)!=env->GetArrayLength(contents))throw std::runtime_error("Invalid conversation history");
        for(jsize i=0;i<env->GetArrayLength(roles);++i) {
            auto role=static_cast<jstring>(env->GetObjectArrayElement(roles,i));
            auto content=static_cast<jstring>(env->GetObjectArrayElement(contents,i));
            common_chat_msg msg;msg.role=utf8(env,role);msg.content=utf8(env,content);input.messages.push_back(msg);
            env->DeleteLocalRef(role);env->DeleteLocalRef(content);
        }
        auto chat=common_chat_templates_apply(m->templates.get(),input);
        common_chat_parser_params parser(chat);parser.parse_tool_calls=false;
        if(!chat.parser.empty())parser.parser.load(chat.parser);
        const auto * vocab=llama_model_get_vocab(m->model);
        int count=-llama_tokenize(vocab,chat.prompt.data(),chat.prompt.size(),nullptr,0,true,true);
        maxTokens=std::clamp<int>(maxTokens,1,512);
        if(count<=0 || count+maxTokens>static_cast<int>(llama_n_ctx(m->ctx)))throw std::runtime_error("GGUF context exceeds 4096 tokens; reset conversation history or shorten input");
        std::vector<llama_token> tokens(count);
        if(llama_tokenize(vocab,chat.prompt.data(),chat.prompt.size(),tokens.data(),count,true,true)<0)throw std::runtime_error("Tokenization failed");
        const auto start=std::chrono::steady_clock::now();
        auto elapsed=[&]{return std::chrono::duration<double,std::milli>(std::chrono::steady_clock::now()-start).count();};
        auto mem=llama_get_memory(m->ctx);
        int prefix=0;
        // Recurrent and sliding-window state may not support arbitrary rollback.
        // Keep the optimization conservative; always decode the last prompt token
        // again so logits belong to the new request, even for identical prompts.
        if(reuse && !llama_model_is_recurrent(m->model) && !llama_model_is_hybrid(m->model)
            && llama_memory_seq_pos_min(mem,0)==0
            && llama_memory_seq_pos_max(mem,0)==static_cast<int>(m->decoded.size())-1) {
            while(prefix<count-1 && prefix<static_cast<int>(m->decoded.size()) && tokens[prefix]==m->decoded[prefix])++prefix;
        }
        if(prefix==0 || !llama_memory_seq_rm(mem,0,prefix,-1)) {
            llama_memory_clear(mem,true);prefix=0;
        }
        m->decoded.resize(prefix);
        for(int i=prefix;i<count;i+=128) {
            if(Abort::check(&abort))throw std::runtime_error("Generation cancelled or timed out");
            auto n=std::min(128,count-i);
            if(llama_decode(m->ctx,llama_batch_get_one(tokens.data()+i,n))!=0)throw std::runtime_error("GGUF prompt decode failed or cancelled");
            m->decoded.insert(m->decoded.end(),tokens.begin()+i,tokens.begin()+i+n);
        }
        // GPU decode may return before execution finishes. Synchronize so input
        // work is not incorrectly reported as output generation time.
        llama_synchronize(m->ctx);
        if(Abort::check(&abort))throw std::runtime_error("Generation cancelled or timed out");
        const double promptMs=elapsed();
        int outputTokens=0;
        double firstTextMs=-1;
        auto sampler=std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)>(llama_sampler_chain_init(llama_sampler_chain_default_params()),llama_sampler_free);
        // Mild repetition penalty prevents needless repeated phrases from
        // consuming the response budget. Apply before probability truncation.
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_penalties(llama_vocab_n_tokens(vocab),64,1.1f,0.0f,0.0f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_top_p(.9f,1));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_temp(.7f));
        llama_sampler_chain_add(sampler.get(),llama_sampler_init_dist(static_cast<uint32_t>(seed)));
        std::string raw,emitted;
        auto emit=[&](bool partial) {
            std::string content=common_chat_parse(raw.substr(0,complete_utf8(raw)),partial,parser).content;
            content.resize(complete_utf8(content));
            if(content.compare(0,emitted.size(),emitted)!=0)throw std::runtime_error("Model output parser changed an already spoken prefix");
            auto delta=content.substr(emitted.size());
            if(!delta.empty()) {
                if(firstTextMs<0)firstTextMs=elapsed();
                auto bytes=env->NewByteArray(delta.size());
                if(!bytes)throw std::runtime_error("Out of memory while streaming");
                env->SetByteArrayRegion(bytes,0,delta.size(),reinterpret_cast<const jbyte *>(delta.data()));
                env->CallVoidMethod(callback,tokenMethod,bytes);env->DeleteLocalRef(bytes);
                if(env->ExceptionCheck())throw std::runtime_error("Generation callback failed");
                emitted=content;
            }
        };
        for(int i=0;i<maxTokens;++i) {
            if(Abort::check(&abort))throw std::runtime_error("Generation cancelled or timed out");
            auto token=llama_sampler_sample(sampler.get(),m->ctx,-1);
            if(llama_vocab_is_eog(vocab,token))break;
            ++outputTokens;
            std::vector<char> piece(256);
            int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true);
            if(n<0) { piece.resize(-n);n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true); }
            if(n<0)throw std::runtime_error("Cannot decode token");
            raw.append(piece.data(),n);emit(true);
            if(i+1<maxTokens) {
                if(llama_decode(m->ctx,llama_batch_get_one(&token,1))!=0)throw std::runtime_error("GGUF generation failed or cancelled");
                m->decoded.push_back(token);
            }
        }
        emit(false);
        if(emitted.empty())throw std::runtime_error("GGUF returned no spoken text; choose a non-thinking chat model");
        auto statsMethod=env->GetMethodID(cls,"stats","(IIJIJ)V");
        if(!statsMethod)throw std::runtime_error("Missing statistics callback");
        env->CallVoidMethod(callback,statsMethod,count,prefix,static_cast<jlong>(promptMs),outputTokens,static_cast<jlong>(elapsed()-promptMs));
        if(env->ExceptionCheck())throw std::runtime_error("Statistics callback failed");
        __android_log_print(ANDROID_LOG_INFO,"PocketGGUF",
            "prompt_tokens=%d reused_tokens=%d prompt_ms=%.0f output_tokens=%d generation_ms=%.0f first_text_ms=%.0f total_ms=%.0f",
            count,prefix,promptMs,outputTokens,elapsed()-promptMs,firstTextMs,elapsed());
    }catch(const std::exception & e){
        if(m) { m->decoded.clear();llama_memory_clear(llama_get_memory(m->ctx),true); }
        error(env,e);
    }
    if(global)env->DeleteGlobalRef(global);
}
extern "C" JNIEXPORT void JNICALL Java_jp_stackchan_pocket_GgufLlm_nativeFree(JNIEnv *,jobject,jlong handle) {
    delete reinterpret_cast<Model *>(handle);
}
