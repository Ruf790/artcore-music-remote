# ArtCore Music Remote

Пульт для [ArtCore Music](https://github.com/Ruf790/artcore-music) на Android: все плейлисты и альбомы с ПК на телефоне, запуск любого трека касанием, ⏯ ⏹ ⏮ ⏭, перемотка, громкость, повтор, случайный порядок, радиостанции и обложки.

*An Android remote for ArtCore Music. English below.*

## Как подключить

1. На ПК: **ArtCore Music 1.8.0+ → Настройки → Пульт → Включить пульт** (Windows один раз спросит разрешение для брандмауэра).
2. Телефон и ПК — в одной сети Wi-Fi. Откройте приложение: ПК найдётся сам (ArtCore Music раз в 2 с сообщает о себе в сеть).
3. Введите 4-значный код из настроек пульта и нажмите **Подключить**. Дальше телефон подключается сам, код больше не нужен; при потере связи — переподключается.

Если ПК не находится (например, гостевая сеть не пропускает широковещательные пакеты), в меню ⋮ есть **Ввести адрес вручную** — адрес ПК показан в тех же настройках.

## Возможности

- Экран **Треки**: вкладки плейлистов, поиск (трек, исполнитель, альбом), группы-альбомы как в ArtCore Music, миниатюры обложек, играющий трек подсвечен; мини-панель с ⏹ и ⏯ внизу.
- Экран **Сейчас играет**: большая обложка, формат (FLAC · 24 bit · 96 kHz), перемотка, случайный порядок, ⏮ ⏹ ⏯ ⏭, повтор, «Далее: …».
- Меню ☰: плейлисты, радио, выбор ПК, настройки (тема светлая / тёмная, язык, отключиться).
- Уведомление и экран блокировки с ⏮ ⏯ ⏹ ⏭ и обложкой, пока музыка играет; кнопки громкости телефона меняют громкость ArtCore Music, когда приложение открыто.
- Планшеты и широкие экраны: треки слева, «Сейчас играет» справа.
- Русский и английский — по языку телефона (или выбрать в настройках).
- Только локальная сеть: ArtCore Music пускает телефоны из 192.168.*, 10.*, 172.16–31.*, по коду и личному токену.

## Сборка

Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 37, WebSocket через OkHttp. `./gradlew assembleRelease` — APK, `./gradlew testDebugUnitTest` — тесты. В GitHub Actions: тесты, APK (артефакт и вложение в опубликованный релиз), скриншоты экранов с эмулятора.

Подпись релиза — ключ из секретов репозитория (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`); без них APK подписывается отладочным ключом из `keystore/debug.keystore`.

---

## English

An Android remote for ArtCore Music: every playlist and album of the PC on the phone, tap a track to play it on the PC, ⏯ ⏹ ⏮ ⏭, seek, volume, repeat, shuffle, radio stations and covers.

**Connecting:** turn the remote on in ArtCore Music 1.8.0+ (Settings → Remote), open the app on the same Wi-Fi — the PC shows up by itself — enter the 4-digit code once. After that the phone connects (and reconnects) by itself. ⋮ → *Enter the address by hand* if the network blocks broadcasts.

Tablets and wide screens: the tracks on the left, the player on the right. Media notification and lock-screen controls while music plays, phone volume keys control ArtCore Music while the app is open, light and dark theme, Russian and English. Local network only; pairing code plus a per-phone token.

Build: `./gradlew assembleRelease`; tests: `./gradlew testDebugUnitTest`. CI builds the APK, attaches it to published releases and takes emulator screenshots.

License: GNU GPL v3, like ArtCore Music.
