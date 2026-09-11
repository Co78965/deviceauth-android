# DeviceAuth (Android)

Клиентская библиотека привязки пользователя к устройству: отпечаток устройства, ключ Ed25519 и challenge–response к вашему auth-сервису.

## Требования

| | |
|---|---|
| ОС | Android **13+** (API **33+**) |
| Язык | Kotlin, Java 11 |
| Сеть | В приложении нужна permission `INTERNET` |
| Backend | Базовый URL с хвостом `/api/...` (см. ниже) |
| Корутины | `register` / `authenticate` — `suspend`, вызывать из корутины |

На эмуляторе и части устройств Android Keystore может быть недоступен: библиотека сама переключается на software-ключ Ed25519.

## Подключение

В `settings.gradle.kts` хоста (если ставите с JitPack):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

В `build.gradle.kts` модуля приложения:

```kotlin
dependencies {
    implementation("com.github.Co78965:deviceauth-android:v1.0.0")
}
```

В манифесте приложения:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

`serviceUrl` должен заканчиваться на `/`, например `https://auth.example.com/`.

## Публичный API

### `DeviceAuthConfig`

| Поле | Тип | По умолчанию | Описание |
|---|---|---|---|
| `serviceUrl` | `String` | — | Базовый URL сервиса, не пустой |
| `appId` | `String` | — | Идентификатор приложения на сервисе, не пустой |
| `sessionTtlMinutes` | `Long` | `2` | TTL локальной сессии после успешного `register` / `authenticate` |
| `prefsName` | `String` | `"device_auth_prefs"` | Имя SharedPreferences |

Пустые `serviceUrl` / `appId` дают `IllegalArgumentException` сразу в конструкторе конфига.

### `DeviceAuth`

Один экземпляр на процесс (`init` идемпотентен).

```kotlin
val auth = DeviceAuth.init(
    context = applicationContext,
    config = DeviceAuthConfig(
        serviceUrl = "https://auth.example.com/",
        appId = "my-app"
    )
)
```

| Метод | Описание |
|---|---|
| `init(context, config): DeviceAuth` | Создаёт или возвращает уже созданный экземпляр |
| `suspend fun register(userId: String): DeviceAuthResult` | Регистрация устройства за пользователем (отпечаток + публичный ключ + подпись challenge) |
| `suspend fun authenticate(userId: String? = null): DeviceAuthResult` | Вход по уже зарегистрированному устройству |
| `fun hasValidSession(): Boolean` | Есть ли локальная сессия, не истёкшая по `sessionTtlMinutes` |
| `fun logout()` | Сбрасывает локальную сессию (`device_id`). Ключ устройства не удаляется |

Пример:

```kotlin
when (val result = auth.register(userId)) {
    is DeviceAuthResult.Success -> { /* result.status, result.userId */ }
    is DeviceAuthResult.MultipleDevices -> { /* у пользователя уже другое устройство */ }
    is DeviceAuthResult.Failure -> { /* result.message */ }
}

if (auth.hasValidSession()) {
    // не дергать authenticate, пока TTL жив
}

auth.logout()
```

## Результаты и ошибки

Все сетевые сценарии закрываются `DeviceAuthResult`, исключения наружу из `register` / `authenticate` не пробрасываются (кроме ошибок `init` / конфига).

### `DeviceAuthResult.Success`

Успешная проверка подписи.

- `status` — строка статуса от сервиса
- `userId` — при `register` тот, что передали; при `authenticate` — из ответа сервиса, иначе аргумент, иначе `""`

### `DeviceAuthResult.MultipleDevices`

Сервис вернул код `multiple_devices`: у пользователя уже привязано другое устройство.

### `DeviceAuthResult.Failure`

`message` — текст для логов / UI. Типичные значения:

| `message` | Когда |
|---|---|
| `device_not_registered` | Устройство не найдено на сервисе (код `device_not_registered`) |
| текст `message` из тела ошибки сервиса | HTTP не 2xx, в JSON есть `error` / `message` |
| `HTTP <код>` | Не 2xx и тело разобрать не удалось |
| `Пустое тело ответа` | 2xx без body |
| `null` или `e.message` | Сеть, TLS, Keystore, прочие runtime-ошибки |

Код сервиса, который библиотека разбирает отдельно:

- `multiple_devices` → `MultipleDevices`
- `device_not_registered` → `Failure("device_not_registered")`
- любой другой `error` из JSON → `Failure(message)`

Ожидаемый JSON ошибки сервиса:

```json
{ "error": "device_not_registered", "message": "..." }
```