# GTNHTweaks

Модульный мод-твикер для GT: New Horizons (Minecraft 1.7.10).

## Модули

Каждый модуль лежит в своей папке `src/main/java/gtnhtweaks/modules/<модуль>` и наследует
`gtnhtweaks.core.TweakModule`. Новые модули регистрируются в `gtnhtweaks.core.ModuleManager`.
Миксины модуля лежат в `src/main/java/gtnhtweaks/mixins/early/<модуль>` (пакет миксинов общий для мода)
и подключаются в `gtnhtweaks.mixins.Mixins` только когда модуль включён.

Конфиги (`config/GTNHTweaks/`):

| Файл | Что настраивает |
| --- | --- |
| `modules.cfg` | включение и отключение модулей |
| `<id модуля>.cfg` | настройки конкретного модуля |

Изменения вступают в силу после перезапуска игры.

### loadingscreen: экран загрузки

Заменяет экран загрузки Minecraft:

- вместо логотипа Mojang рисуется логотип **GTNH**;
- полосы прогресса загрузки модов;
- таймер загрузки сборки (от старта JVM до главного меню);
- время предыдущего запуска и сколько процентов от него уже прошло;
- использование памяти;
- время загрузки в левом верхнем углу главного меню.

Работает со всеми вариантами экрана загрузки:

- ванильный экран Mojang до загрузки модов;
- сплэш Forge (`config/splash.properties`, `enabled=true`);
- BetterLoadingScreen (есть в GTNH): он продолжает отслеживать прогресс,
  а этот модуль рисует свой экран поверх каждого его кадра.

Настройки в `config/GTNHTweaks/loadingscreen.cfg`:

| Параметр | По умолчанию | Описание |
| --- | --- | --- |
| `general/style` | `DARK` | стиль: `DARK` (тёмный) или `LIGHT` (светлый) |
| `general/showTimer` | `true` | таймер загрузки |
| `general/showPreviousLaunch` | `true` | время предыдущего запуска |
| `general/showMemory` | `true` | использование памяти |
| `general/showLoadTimeInMainMenu` | `true` | время загрузки в главном меню |
| `integration/replaceVanillaScreen` | `true` | заменять экран с логотипом Mojang |
| `integration/replaceForgeSplash` | `true` | рисовать поверх сплэша Forge |
| `integration/drawOverBetterLoadingScreen` | `true` | рисовать поверх BetterLoadingScreen |
| `text/*` | английский | подписи на экране, поддерживается кириллица |

Время последней загрузки хранится в `config/GTNHTweaks/loadingscreen-stats.properties`.
