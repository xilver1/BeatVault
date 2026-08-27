Write-Host '======================================'
Write-Host ' BeatVault CLI - Android Cross-Compiler'
Write-Host '======================================'

Write-Host '[1/2] Installing/updating cross toolchain...'
cargo install cross --git https://github.com/cross-rs/cross

Write-Host '[2/2] Cross-compiling for Android (aarch64-linux-android)...'
Push-Location bvault-app
cross build --target aarch64-linux-android --release -p bvault-cli
Pop-Location

Write-Host '======================================'
Write-Host 'Build Complete!'
Write-Host 'Your Android-ready binary is located at:'
Write-Host 'C:\repos\BeatVault\bvault-app\target\aarch64-linux-android\release\bvault'
Write-Host ''
Write-Host 'Next Steps:'
Write-Host '1. Copy the bvault file to your phone.'
Write-Host '2. Mark it as executable in Termux (chmod +x bvault) and run it!'
Write-Host '======================================'
