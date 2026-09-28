param([Parameter(Mandatory=$true)][string]$BuildRoot,[Parameter(Mandatory=$true)][string]$SdkRoot)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($BuildRoot).Replace('\','/')
$sdk=[IO.Path]::GetFullPath($SdkRoot).Replace('\','/')
New-Item -ItemType Directory -Force $root | Out-Null
$archive="$root/compiler.zip"
$url='https://github.com/mstorsjo/llvm-mingw/releases/download/20260922/llvm-mingw-20260922-ucrt-x86_64.zip'
$expected='E3AD77D117A4BEA19A7A3B333341824D79A5A371004A10E25B8504E7B3047666'
if(!(Test-Path $archive)){Invoke-WebRequest $url -OutFile $archive}
if((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected){throw 'Compiler checksum mismatch'}
$compiler="$root/compiler/llvm-mingw-20260922-ucrt-x86_64"
if(!(Test-Path "$compiler/bin/clang++.exe")){Expand-Archive $archive "$root/compiler"}
$pins=@{
    'Vulkan-Headers'='6802bb4733b63ed5efd3adb308a6c885ef180ea1'
    'Vulkan-Hpp'='d76754446ad063a610e6f9a70f66a403386020c0'
    'SPIRV-Headers'='cb42dec3830d3ac67fa449ecdc0c0f73d5e74498'
}
foreach($name in $pins.Keys) {
    $path="$root/$name"
    if(!(Test-Path $path)) {
        git init $path
        git -C $path remote add origin "https://github.com/KhronosGroup/$name.git"
        git -C $path fetch --depth 1 origin $pins[$name]
        if($LASTEXITCODE -ne 0){throw "Fetch failed: $name"}
        git -C $path checkout --detach FETCH_HEAD
    }
    $actual=git -C $path rev-parse HEAD
    if($LASTEXITCODE -ne 0 -or $actual -ne $pins[$name]){throw "Unexpected revision: $name"}
    if(git -C $path status --porcelain){throw "Modified dependency: $name"}
}
@"
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_C_COMPILER "$compiler/bin/clang.exe")
set(CMAKE_CXX_COMPILER "$compiler/bin/clang++.exe")
set(CMAKE_EXE_LINKER_FLAGS "-static")
set(CMAKE_MAKE_PROGRAM "$sdk/cmake/3.22.1/bin/ninja.exe" CACHE FILEPATH "" FORCE)
"@ | Set-Content "$root/host-toolchain.cmake"
$cmake="$sdk/cmake/3.22.1/bin/cmake.exe"
& $cmake -S "$root/SPIRV-Headers" -B "$root/spirv-build" -G Ninja "-DCMAKE_MAKE_PROGRAM=$sdk/cmake/3.22.1/bin/ninja.exe" "-DCMAKE_TOOLCHAIN_FILE=$root/host-toolchain.cmake" "-DCMAKE_INSTALL_PREFIX=$root/spirv-install" -DSPIRV_HEADERS_ENABLE_TESTS=OFF
if($LASTEXITCODE -ne 0){throw 'SPIR-V configure failed'}
& $cmake --install "$root/spirv-build"
if($LASTEXITCODE -ne 0){throw 'SPIR-V install failed'}
Write-Output "Ready. Set POCKET_VULKAN_ROOT=$root for the Gradle build."
