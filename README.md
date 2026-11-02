# DeepSweep — «Глубинный сапёр»

Бесконечный сапёр на дне океана для Android: сектора 8×8, шесть режимов, девять бустеров,
хранилища, достопримечательности, уровни, достижения и ежедневные задания. Без рекламы,
трекеров и доступа к интернету — весь прогресс хранится на устройстве.

Пакет: `com.skeorz.lab.deepsweep` · версия 1.0.1 (free-код 2, Pro-код 100002) · minSdk 26 · targetSdk 37.

## Сборки

| Вариант | Для чего | Premium | Читы |
|---|---|---|---|
| `free` | RuStore, бесплатная версия | нет | нет |
| `pro` | GitHub Releases, покупка Premium | по коду активации | нет |
| `debug` | отладка | да | да |

Pro-сборка ставится поверх free: тот же пакет и подпись, прогресс сохраняется. Код активации
проверяется офлайн; покупка, выпуск кодов и ограничения описаны в [`docs/PRO.md`](docs/PRO.md).

Поддержать разработку и получить ключ Premium: [пост на Boosty](https://boosty.to/skeorz/posts/45a69380-9ff8-43ae-89d3-02e0eb816165),
код присылает бот [@deepsweep_pro_bot](https://t.me/deepsweep_pro_bot).

## Стек

- Kotlin 2.4, Jetpack Compose (Material 3), kotlinx.serialization;
- собственный игровой движок: детерминированная генерация по seed, без сторонних библиотек;
- подпись лицензий: ECDSA P-256 / SHA-256 на встроенных средствах Java, без зависимостей;
- сборка: Gradle 9.8 (wrapper), AGP 9.4, JDK 17+.

## Сборка

Нужен установленный Android SDK (compileSdk 37) и путь к нему в `local.properties`:

```properties
sdk.dir=C\:\\Android\\Sdk
```

```bash
./gradlew :app:assembleProDebug       # тестовая сборка: applicationId .test, Premium и читы
./gradlew :app:testProDebugUnitTest   # юнит-тесты игровой логики и лицензий
./gradlew :app:assembleFreeRelease    # RuStore: без Premium и покупок
./gradlew :app:assembleProRelease     # Pro: Premium по коду активации
```

Release-сборки подписываются ключом, описанным в `keystore.properties` в корне проекта
(файл и сам `.jks` в git не попадают):

```properties
storeFile=deepsweep-release.jks
storePassword=…
keyAlias=deepsweep
keyPassword=…
```

Без `keystore.properties` собираются только debug-варианты.

## Структура

| Путь | Что внутри |
|---|---|
| `app/src/main/java/.../game/` | `World` (генерация поля), `Session` (правила прогона), `Mode` |
| `app/src/main/java/.../meta/` | `Store` (прогресс, уровни, задания), `Catalog`, `License` (проверка кодов) |
| `app/src/main/java/.../ui/` | экраны Compose, поле, бустеры, эффекты, темы |
| `app/src/test/` | юнит-тесты логики (JVM) |
| `tools/license/` | выпуск ключей и кодов активации (приватный ключ в git не попадает) |
| `supabase/` | бот выдачи ключей: Edge Function `tg-bot` и миграция (см. [`docs/BOT.md`](docs/BOT.md)) |
| `docs/` | покупка и активация Pro, настройка бота |
| `rustore/` | карточка RuStore, политика конфиденциальности, скриншоты |

## Публикация

- RuStore: [`rustore/RuStore.md`](rustore/RuStore.md), сборка `freeRelease`.
- Pro на GitHub: [`docs/PRO.md`](docs/PRO.md), сборка `proRelease`.

## Лицензия

Все права защищены. Исходный код предоставляется для ознакомления и сборки;
распространение, публикация и производные работы — только с разрешения автора.
