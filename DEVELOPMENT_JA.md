# ソースをビルドする方へ

AndroidはAndroid StudioでAndroid_Sourceを開き、JDK 17とAndroid SDK 35を用意して同期します。SDKの位置は各自の環境で設定してください。Gradle Wrapperを同梱しています。コマンドならそのフォルダで`gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`を実行します。

配布者の署名鍵・local.properties・キャッシュ・開発ログは同梱しません。ソースのdebug署名は各自の標準debug鍵を使用します。そのため自分のビルドは同梱APKへ上書き更新できない場合があります。既存アプリを削除する前に、モデル原本と必要な設定を控えてください。

Core2はCore2_Sourceのplatformio.iniを使用します。固定のupload_portは指定せず、接続先に合わせます。本パッケージはソース書き込み方式で、開発PCの絶対パスを含み得るELFやビルド生成物を含めません。

第三者のライセンス・著作権表示は個人情報の削除対象とせず維持しています。Android_Source/THIRD_PARTY.mdと各LICENSEを参照してください。


APKはdebug署名版であり、一般向けストア配布用のrelease署名・ストア審査は別工程です。

## 0.3.50以降：GGUFネイティブ依存

Android_Sourceで作業します。JDK17・SDK35に加え、NDK `28.2.13676358`、CMake `3.22.1`をAndroid SDK Managerで用意してください。対象ABIはarm64-v8aです。

```powershell
git clone https://github.com/ggml-org/llama.cpp vendor/llama.cpp
git -C vendor/llama.cpp checkout 81bc6b83f827df746eb129235488d325c49cae52
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-PpocketVulkanRoot='
```

上はCPU専用ビルドです。配布APKはCPUとVulkanを含みます。WindowsでVulkanもビルドする場合は次の手順を使います。パスは自分の環境に置き換えてください。

```powershell
./tools/setup-vulkan.ps1 -BuildRoot C:/android-deps/vulkan -SdkRoot C:/Android/Sdk
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug '-PpocketVulkanRoot=C:/android-deps/vulkan'
```

スクリプトは固定版のVulkan依存とホスト用コンパイラーを取得します。CPU/Vulkan切り替えは`pocketVulkanRoot`を明示してください。APK内の`lib/arm64-v8a/libggml-vulkan.so`でVulkan同梱を確認できます。依存ソース・SDK・署名秘密鍵はリポジトリに含めません。

配布版と同じ鍵で更新するビルドでは`STACKCHAN_DEBUG_KEYSTORE`に鍵のパスを指定できます。鍵は公開しないでください。
