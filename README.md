# StackChan Pocket Core2

## 眠っているCore2を、もう一度しゃべる相棒に。

Androidスマホが音声認識・LLM・音声合成を担当し、Core2は声・表情・しぐさで応えるスタックチャンに。
Wi-Fi／USB接続に対応。対応モデルとオフライン日本語音声を準備すれば、ネットのない場所でも会話できます。

**Core2でも、ここまでできる。**

手元のCore2を活用するための、AndroidアプリとCore2ファームです。スマホをポケットに入れ、本体から聞き取りを開始する使い方もできます。

## この配布物

Android 0.3.45 / Core2 0.3.32、暫定「軽快会話版」の先行公開版です。「軽快」は現在のモデル・端末との組み合わせでの評価です。モデルや端末によって速度と回答品質は変わります。

**本リリースはM5Stack Core2専用です。** AndroidアプリのUSB接続対象はCore2のCP2104／CH9102Fに限定しています。CoreS3はUSB未対応、Wi-Fi接続を含む実機動作は未検証です。今後、実機検証のうえ対応を検討します。Core2用ファームをCoreS3へ書き込まないでください。

## できること

- Wi-Fi／USB接続。マイクとスピーカーはそれぞれCore2／スマホを選択。
- スマホ内の音声認識・LLM・音声合成、生成途中からの読み上げ。
- 本体の口パク・表情・サーボ操作、音量調整、音声による本体操作。
- 天気・ニュースの読み上げ。新しい情報の取得にはネット接続が必要です。
- CPU／GPU選択、声の速さ・高さ、履歴再利用、処理時間のコピー。

LLMはLiteRT-LM対応の.litertlm形式を使用します。GGUF／llama.cppは未対応です。モデルは同梱しません。

## 最初に必要なモデルをダウンロード

**APKだけでは会話できません。次の4ファイルを別途取得して、スマホのアプリへ取り込んでください。** モデルは配布ZIPに含まれません。

|取り込みボタン|選ぶファイル|入手先・役割|
|---|---|---|
|音声認識 ONNX|`model.int8.onnx`|[SenseVoice配布アーカイブ](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2)。声を文字に変換|
|音声認識 tokens.txt|`tokens.txt`|上と同じアーカイブ内のファイル|
|Silero VAD ONNX|`silero_vad.onnx`|[ダウンロード](https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx)。話し始め・話し終わりを判定|
|LLM .litertlm|`gemma-4-E2B-it.litertlm` **または** `gemma-4-E4B-it.litertlm`|[E2B配布ページ](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/main)／[E4B配布ページ](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/tree/main)。返答を生成|

**LLMはどちらか1つです。** E2Bは軽快さ、E4Bは回答品質を重視する場合の候補です。この手順では名前に`-web`や`-gpu`の付かない上記ファイルを選びます。GGUFの拡張子を変更しても使えません。

1. SenseVoiceの`.tar.bz2`を展開し、`model.int8.onnx`と`tokens.txt`を取り出します。`.tar`が出たらもう一度展開します。VADとLLMは展開不要です。
2. スマホの内部ストレージに`Download/StackChanModels`フォルダを作り、4ファイルを保存します。PCで取得した場合はUSBの「ファイル転送」でコピーします。**Core2のSDカードには入れません。**
3. アプリの処理を終了し、設定→端末内モデル→「モデルの取り込みを開く / 閉じる」で、各ボタンから対応するファイルを選びます。
4. 各ファイルの取り込み完了を待ち、4つ揃ったら「モデルを読み込む」を押します。

保存先は上記以外でも選択できます。取り込み時にアプリ内へコピーされるため、元ファイルとコピーの両方に空き容量が必要です。元ファイルは再導入用に保管してください。

**読み上げ用には、Androidの音声エンジン設定でオフライン日本語音声も準備します。** これは上記4ファイルとは別です。

展開方法・保存場所の図・取り込み・モデル変更の詳細は **[モデルの取得・保存・取り込み](MODELS_JA.md)** を参照してください。

## 初めて使う方へ

1. [導入手順](README_JA.md)で必要な機材とAPKのインストールを確認。
2. [モデル取得・保存場所](MODELS_JA.md)に沿って4ファイルと日本語音声を準備。
3. Core2_SourceをPlatformIOで書き込み、[操作方法](USAGE_JA.md)に沿って接続。
4. 困ったときは[FAQ](FAQ_JA.md)。

## 関連ドキュメント

- [更新内容](RELEASE_NOTES_JA.md)
- [計測の意味と限界](PERFORMANCE_JA.md)
- [ビルド手順](DEVELOPMENT_JA.md)
- [原作者・謝辞](CREDITS_JA.md)

APKはdebug署名の先行配布用です。ストア公開版ではありません。モデル・署名秘密鍵・個人設定・会話データは同梱していません。各ライセンスを確認して利用してください。


モデル選択：E2Bは軽快さ、E4Bは回答品質を重視する比較候補です。E4Bは利用者によるGPU会話の動作報告あり。[導入方法と確認範囲](MODELS_JA.md)を参照してください。




## 開発の由来

本リポジトリは独立した派生プロジェクトです。robo8080さんの[AI_StackChan2](https://github.com/robo8080/AI_StackChan2)を出発点とした開発・操作性を引き継ぎ、現行Core2ファームは[Corvelis/stackchan-pet-fw](https://github.com/Corvelis/stackchan-pet-fw)を基盤にしています。Androidアプリは本プロジェクトの独自実装です。ｽﾀｯｸﾁｬﾝの発案者ししかわさんと[Stack-chan community](https://github.com/stack-chan/)に感謝します。

各原作者による公式配布版ではありません。著作権・ライセンスの範囲は[クレジット](CREDITS_JA.md)と[ライセンス案内](LICENSES.md)を参照してください。

## ダウンロード

[APKと配布ZIPのダウンロード](https://github.com/Nonchira/StackChan-Pocket-Core2/releases/tag/v0.3.45-core2-0.3.32)。モデルは別途取得してください。


## ライセンス
Androidアプリを含む独自実装は[MIT License](LICENSE)です。既存コード・依存ライブラリの許諾と著作権表示は保持します。詳細は[ライセンス案内](LICENSES.md)を参照してください。
