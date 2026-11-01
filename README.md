# DeepSweep — «Глубинный сапёр»

Бесконечный сапёр на дне океана для Android: сектора 8×8, шесть режимов, девять бустеров,
хранилища, достопримечательности, уровни, достижения и ежедневные задания. Без рекламы,
без встроенных покупок и без доступа к интернету — весь прогресс хранится на устройстве.

Пакет: `com.skeorz.lab.deepsweep` · версия 1.0.0 (код 1) · minSdk 26 · targetSdk 37.

## Стек

- Kotlin 2.4, Jetpack Compose (Material 3), kotlinx.serialization;
- собственный игровой движок: детерминированная генерация по seed, без сторонних библиотек;
- сборка: Gradle 9.8 (wrapper), AGP 9.4, JDK 17+.

## Сборка

Нужен установленный Android SDK (compileSdk 37) и путь к нему в `local.properties`:

```properties
sdk.dir=C\:\\Android\\Sdk
```

```bash
./gradlew :app:assembleDebug      # тестовая сборка: applicationId .test, Premium и читы
./gradlew :app:testDebugUnitTest  # юнит-тесты игровой логики
./gradlew :app:assembleRelease    # магазинная сборка (нужен keystore.properties)
```

Release-сборка подписывается ключом, описанным в `keystore.properties` в корне проекта
(файл и сам `.jks` в git не попадают):

```properties
storeFile=deepsweep-release.jks
storePassword=…
keyAlias=deepsweep
keyPassword=…
```

Без `keystore.properties` собирается только debug-вариант.

## Структура

| Путь | Что внутри |
|---|---|
| `app/src/main/java/.../game/` | `World` (генерация поля), `Session` (правила прогона), `Mode` |
| `app/src/main/java/.../meta/` | `Store` (прогресс, уровни, задания), `Catalog` (предметы, достижения) |
| `app/src/main/java/.../ui/` | экраны Compose, поле, бустеры, эффекты, темы |
| `app/src/test/` | юнит-тесты логики (JVM) |
| `rustore/` | карточка RuStore, политика конфиденциальности, скриншоты |

## Публикация

Инструкция и тексты для RuStore — в [`rustore/RuStore.md`](rustore/RuStore.md).

## Лицензия

Все права защищены. Исходный код предоставляется для ознакомления и сборки;
распространение, публикация и производные работы — только с разрешения автора.
