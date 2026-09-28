Android original implementation: MIT; see LICENSE. Third-party licenses below remain unchanged.

# Third-party components

This independent prototype is not the closed-beta StackChan Pet Talk app.

| Component | Version | License / source |
|---|---|---|
| sherpa-onnx Android AAR | 1.13.8 | Apache-2.0, https://github.com/k2-fsa/sherpa-onnx |
| LiteRT-LM Android | 0.17.1 | Apache-2.0, https://github.com/google-ai-edge/LiteRT-LM |
| ONNX Runtime (inside sherpa AAR) | supplied by upstream | MIT, https://github.com/microsoft/onnxruntime |
| OkHttp | 4.12.0 | Apache-2.0, https://github.com/square/okhttp |
| Okio / Kotlin / kotlinx.coroutines / Gson | transitive dependencies | Apache-2.0; upstream metadata in Gradle dependency cache |
| Android TextToSpeech engine / voice | installed separately by user | engine and voice provider terms |

License texts are in app/src/main/assets/licenses and are included in the APK.
AI models are not bundled; their licenses and download terms are separate.
Weather data: Japan Meteorological Agency and Open-Meteo (CC BY 4.0 attribution for Open-Meteo). Sources are attributed in the UI and spoken forecasts.
Core2 firmware is a separate MIT-licensed derivative of Corvelis/stackchan-pet-fw; its LICENSE is preserved with its sources.

- USB serial: usb-serial-for-android 3.11.0, MIT license. Copyright Google Inc. / Mike Wakerly. License included in USB_SERIAL_LICENSE.txt and APK assets/usb-serial-license.txt. Source: https://github.com/mik3y/usb-serial-for-android/tree/3.11.0

## ベースとなった作品・謝辞

本プロジェクトは、robo8080さんのAIｽﾀｯｸﾁｬﾝ（AI_StackChan2）をベースとした開発を出発点に、スマホ上のローカルLLMを前提とする仕様へ変更し、USB通信対応などの機能拡張を行っています。

- 開発の出発点となった直接のソース元：robo8080さんの [AI_StackChan2](https://github.com/robo8080/AI_StackChan2)
- robo8080さん：[GitHub](https://github.com/robo8080)
- ｽﾀｯｸﾁｬﾝの発案者：ししかわさん。関連プロジェクト：[Stack-chan community](https://github.com/stack-chan/)

現行のスマホ連携版Core2ファームには、[Corvelis/stackchan-pet-fw](https://github.com/Corvelis/stackchan-pet-fw)を基盤とした実装も使用しています。上記の開発経緯と、現行コードの由来の両方を記載しています。

原作者および各ライブラリの開発者の皆さまに感謝します。本版は独自の派生開発であり、各原作者の公式配布版ではありません。既存の著作権表示・LICENSE・第三者ライセンス表記を維持します。

## GGUF backend

- llama.cpp: commit `81bc6b83f827df746eb129235488d325c49cae52`, MIT, https://github.com/ggml-org/llama.cpp
- cpp-httplib: MIT; nlohmann/json: MIT. License texts retained in APK assets/licenses.
- Vulkan-Headers, Vulkan-Hpp, SPIRV-Headers: their upstream license texts are retained in assets/licenses subdirectories. Dependency revisions are pinned in tools/setup-vulkan.ps1.
- The Vulkan backend is optional at build time. AI model weights are not bundled and retain their own terms.
