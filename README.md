# WarrantyKeeper - Android приложение для хранения гарантийных документов

## Описание

WarrantyKeeper - это Android-приложение для хранения чеков и гарантийных документов с автоматическим извлечением информации о гарантии и напоминаниями об истечении срока.

### Основные возможности (MVP):
- ✅ Вход через Google Account
- ✅ Съемка документов (камера + галерея)
- ✅ Автоматическое распознавание текста (ML Kit OCR - полностью бесплатно, работает offline)
- ✅ Извлечение информации о гарантии (дата покупки, срок, название товара)
- ✅ Локальное хранилище (Room Database + файловая система)
- ✅ Список документов с фильтрацией и поиском
- ✅ Базовые уведомления об истечении гарантии
- ✅ Настройки уведомлений

## Технологический стек

- **Язык**: Kotlin
- **Минимальная версия Android**: API 26 (Android 8.0)
- **Архитектура**: MVVM + Clean Architecture
- **UI**: Jetpack Compose + Material 3
- **База данных**: Room
- **DI**: Hilt
- **Камера**: CameraX
- **OCR**: Google ML Kit Text Recognition (бесплатно, offline)
- **Навигация**: Jetpack Navigation Compose
- **Асинхронность**: Kotlin Coroutines + Flow
- **Изображения**: Coil

## Настройка проекта

### 1. Требования

- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK API 26+
- Gradle 8.2+

### 2. Клонирование проекта

```bash
# Если у вас есть архив, распакуйте его
unzip WarrantyKeeper.zip
cd WarrantyKeeper
```

### 3. Настройка Firebase и Google Sign-In

#### 3.1. Создание проекта в Firebase Console

1. Перейдите на https://console.firebase.google.com/
2. Нажмите "Add project" (Создать проект)
3. Введите название проекта: `WarrantyKeeper`
4. Следуйте инструкциям мастера создания проекта

#### 3.2. Добавление Android приложения

1. В консоли Firebase выберите ваш проект
2. Нажмите на иконку Android
3. Введите package name: `com.warrantykeeper`
4. Введите App nickname (опционально): `WarrantyKeeper`
5. Введите SHA-1 certificate fingerprint:

**Получение SHA-1:**

```bash
# Для debug keystore
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Найдите строку SHA1 и скопируйте её
# Пример: SHA1: 3B:6F:A2:...
```

6. Скачайте файл `google-services.json`
7. Поместите файл в: `WarrantyKeeper/app/google-services.json`

#### 3.3. Включение Google Sign-In

1. В Firebase Console перейдите в `Authentication` → `Sign-in method`
2. Включите провайдер `Google`
3. Укажите email поддержки проекта
4. Сохраните

### 4. Открытие проекта в Android Studio

1. Откройте Android Studio
2. File → Open
3. Выберите папку `WarrantyKeeper`
4. Дождитесь синхронизации Gradle

### 5. Первый запуск

1. Подключите Android устройство или запустите эмулятор (API 26+)
2. Нажмите Run (зеленая кнопка Play)
3. При первом запуске:
   - Разрешите доступ к камере
   - Войдите через Google аккаунт

## Структура проекта

```
app/
├── data/
│   ├── local/
│   │   ├── database/          # Room Database
│   │   │   ├── AppDatabase.kt
│   │   │   ├── DocumentDao.kt
│   │   │   └── DocumentEntity.kt
│   │   └── prefs/             # DataStore Preferences
│   │       └── PreferencesManager.kt
│   └── repository/
│       └── DocumentRepository.kt
├── domain/
│   ├── model/                 # Domain модели
│   │   └── Document.kt
│   └── usecase/              # Use Cases
│       └── AddDocumentUseCase.kt
├── presentation/             # UI слой
│   ├── login/
│   │   ├── LoginScreen.kt
│   │   └── LoginViewModel.kt
│   ├── main/
│   │   ├── MainScreen.kt
│   │   └── MainViewModel.kt
│   ├── camera/
│   │   ├── CameraScreen.kt
│   │   └── CameraViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── MainActivity.kt
├── utils/
│   ├── OCRProcessor.kt       # ML Kit OCR обработка
│   ├── FileHelper.kt         # Работа с файлами
│   └── NotificationHelper.kt # Уведомления
├── di/
│   └── AppModule.kt          # Hilt модули
└── WarrantyKeeperApp.kt      # Application класс
```

## Использование приложения

### 1. Первый вход

При первом запуске приложения:
1. Нажмите "Войти через Google"
2. Выберите Google аккаунт
3. Предоставьте необходимые разрешения

### 2. Добавление документа

1. Нажмите на кнопку "+" (FAB)
2. Разрешите доступ к камере (если не разрешили ранее)
3. Сфотографируйте документ или выберите из галереи
4. Выберите тип документа:
   - "Да, гарантия" - приложение автоматически распознает текст и попытается извлечь:
     * Название товара
     * Дату покупки
     * Срок гарантии
     * Название магазина
   - "Нет, обычный чек" - просто сохранит фото

### 3. Просмотр документов

- Главный экран показывает все документы
- Используйте поиск для быстрого нахождения нужного документа
- Фильтруйте по типу: Все / Гарантии / Чеки / Активные
- Нажмите на карточку для просмотра деталей

### 4. Уведомления

- Приложение автоматически отправит уведомление за N дней до истечения гарантии
- Настройте период уведомлений в разделе "Настройки"
- Доступные периоды: 1, 3, 7, 14, 30 дней

## Особенности реализации OCR

### Google ML Kit - бесплатное решение

В отличие от других OCR API, **Google ML Kit полностью бесплатен** и работает **offline** на устройстве пользователя.

**Преимущества:**
- ✅ Бесплатно (нет лимитов на количество запросов)
- ✅ Работает offline (не требует интернет-соединения)
- ✅ Быстрая обработка (на устройстве)
- ✅ Конфиденциальность (данные не отправляются на сервер)
- ✅ Не требует API ключей

**Алгоритм работы:**

1. ML Kit распознает весь текст на изображении
2. `OCRProcessor` анализирует распознанный текст с помощью регулярных выражений:
   - Ищет даты в форматах: `dd.MM.yyyy`, `dd/MM/yyyy`, `yyyy-MM-dd`
   - Ищет упоминания гарантии: "гарантия 12 месяцев", "warranty 24 months"
   - Вычисляет дату окончания: дата покупки + срок гарантии
3. Пользователь может отредактировать распознанные данные перед сохранением

## Сборка Release APK

### 1. Создание Keystore

```bash
keytool -genkey -v -keystore warranty-keeper.keystore -alias warranty-keeper -keyalg RSA -keysize 2048 -validity 10000
```

### 2. Настройка подписи

Создайте файл `keystore.properties` в корне проекта:

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=warranty-keeper
storeFile=../warranty-keeper.keystore
```

### 3. Обновление build.gradle.kts

Убедитесь, что в `app/build.gradle.kts` есть конфигурация для release:

```kotlin
signingConfigs {
    create("release") {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            val keystoreProperties = Properties()
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
}

buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### 4. Сборка

```bash
./gradlew assembleRelease
```

APK будет находиться в: `app/build/outputs/apk/release/app-release.apk`

## Решение проблем

### Ошибки компиляции

1. **"Could not find google-services.json"**
   - Убедитесь, что файл `google-services.json` находится в папке `app/`

2. **Hilt ошибки**
   - File → Invalidate Caches → Invalidate and Restart

3. **CameraX ошибки**
   - Проверьте разрешения в AndroidManifest.xml
   - Убедитесь, что устройство/эмулятор имеет камеру

### Проблемы с Google Sign-In

1. **Не работает вход**
   - Проверьте SHA-1 в Firebase Console
   - Убедитесь, что Google Sign-In включен в Authentication
   - Проверьте package name (`com.warrantykeeper`)

2. **"Developer Error"**
   - Пересоздайте `google-services.json`
   - Проверьте, что используется правильный debug keystore

### Проблемы с OCR

1. **Не распознается текст**
   - Убедитесь, что фото четкое и хорошо освещено
   - ML Kit лучше работает с печатным текстом
   - Попробуйте сфотографировать еще раз

2. **Неправильно извлекается дата**
   - Можно вручную отредактировать данные после распознавания
   - Проверьте формат даты на документе

## Дальнейшее развитие

### Планируемые функции (не в MVP):

- [ ] Синхронизация с Google Drive
- [ ] Продвинутая обработка AI (улучшенное извлечение данных)
- [ ] Редактирование документов
- [ ] Детальный просмотр документа
- [ ] Расширенные фильтры и сортировка
- [ ] Экспорт данных
- [ ] Темная тема
- [ ] Многоязычность
- [ ] Статистика и аналитика

## Лицензия

MIT License

## Контакты

По вопросам и предложениям:
- Email: support@warrantykeeper.app

---

**Важно:** Приложение использует **полностью бесплатные технологии**:
- Google ML Kit OCR - бесплатно, без лимитов
- Firebase Authentication - бесплатно для базового использования
- Room Database - локальная база данных, бесплатно

Никаких платных API или сервисов не требуется!
