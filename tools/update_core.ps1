# Lay ban loi moi nhat
$ErrorActionPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Set-Location (Split-Path $PSScriptRoot -Parent)
Remove-Item extractor.txt -ErrorAction SilentlyContinue
try {
    $t = (Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/TeamNewPipe/NewPipe/dev/gradle/libs.versions.toml').Content
    $m = [regex]::Match($t, '(?m)^teamnewpipe-newpipe-extractor\s*=\s*"([^"]+)"')
    if ($m.Success) { Set-Content -Encoding ascii extractor.txt $m.Groups[1].Value }
} catch {}
try {
    $f = 'app\src\main\java\com\linkinlt\tubelite\YoutubeHttpDataSource.java'
    $s = (Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/TeamNewPipe/NewPipe/dev/app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java').Content
    $s = $s -replace 'package org\.schabi\.newpipe\.player\.datasource;', 'package com.linkinlt.tubelite;'
    $s = $s -replace 'import org\.schabi\.newpipe\.DownloaderImpl;', 'import com.linkinlt.tubelite.DownloaderImpl;'
    if ($s -match 'package com\.linkinlt\.tubelite;') {
        Copy-Item $f 'YoutubeHttpDataSource.bak' -Force
        [IO.File]::WriteAllText((Join-Path (Get-Location) $f), $s)
    }
} catch {}
