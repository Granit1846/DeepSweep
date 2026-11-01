package com.skeorz.lab.deepsweep.meta

object S {
    const val TILES = "tiles"
    const val SECTORS = "sectors"
    const val NO_FLAGS = "sectors_no_flags"
    const val FLAGS_OK = "flags_correct"
    const val MINES_HIT = "mines_hit"
    const val CRYSTAL_TILES = "crystal_tiles"
    const val CRYSTALS_EARNED = "crystals_earned"
    const val CRYSTALS_SPENT = "crystals_spent"
    const val SPECIALS = "specials"
    const val VAULTS = "vaults"
    const val BOOSTERS = "boosters_used"
    const val MAX_DIST = "max_distance"
    const val LEVEL = "level"
    const val GAMES = "games"
    const val MISSIONS = "missions_done"
    const val STREAK = "best_login_streak"
    const val CLEAN = "best_clean_series"
    const val CLIMB = "best_climb"
    const val BEST_HARDCORE = "best_hardcore"
    const val BEST_TIMED = "best_timed"
    const val CHORDS = "chords"
    const val SCORE = "score_total"
    const val CHAINS = "chains"
    const val LANDMARKS = "landmarks"
}

/** Russian plural form: plural(n, "сектор", "сектора", "секторов"). */
fun plural(n: Long, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1L && m100 != 11L -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

const val CHEST_EVERY = 25
const val CHEST_REWARD = 8

val STAT_NAMES = linkedMapOf(
    S.TILES to "Открыто клеток",
    S.SECTORS to "Решено секторов",
    S.NO_FLAGS to "Секторов без буёв",
    S.FLAGS_OK to "Верных буёв",
    S.MINES_HIT to "Подрывов",
    S.SPECIALS to "Особых секторов",
    S.VAULTS to "Вскрыто хранилищ",
    S.BOOSTERS to "Использовано бустеров",
    S.MAX_DIST to "Дальность (секторов)",
    S.CLIMB to "Высота всплытия",
    S.CLEAN to "Лучшая серия без ошибок",
    S.CRYSTAL_TILES to "Найдено подарков",
    S.CHAINS to "Цепных реакций",
    S.LANDMARKS to "Исследовано достопримечательностей",
    S.SCORE to "Очков за всё время",
    S.CRYSTALS_EARNED to "Заработано кристаллов",
    S.CRYSTALS_SPENT to "Потрачено кристаллов",
    S.GAMES to "Погружений",
)

val TIER_NAMES = listOf("Бронза", "Серебро", "Золото", "Платина")

/**
 * Tiered achievements have 4 goals (bronze…platinum) and the reward doubles with every tier;
 * one-time achievements have a single goal. [frame] is granted at the last tier.
 */
class Achievement(
    val id: String, val name: String, val stat: String, val goals: List<Long>, val reward: Int,
    val frame: String? = null, val desc: (Long) -> String,
) {
    val tiered get() = goals.size > 1
    fun rewardFor(tier: Int) = reward shl (tier - 1)

    /** Booster pack size for a tier: silver and platinum normally, every tier with premium. */
    fun packFor(tier: Int, premium: Boolean) = when {
        !tiered -> 0
        premium -> PREMIUM_PACKS[tier - 1]
        tier == 2 -> 12
        tier == 4 -> 24
        else -> 0
    }
}

/** Booster id → weight in a random pack. */
val PACK_WEIGHTS = listOf(
    // the stronger the booster, the rarer it is in packs (sum = 100)
    "sonar" to 25, "oxygen" to 15, "trawl" to 14, "flash" to 12, "defuse" to 10,
    "echo" to 9, "shield" to 6, "lockpick" to 5, "solve" to 4,
)

/** Boosters that work at once, without picking a tile or sector on the board. */
val INSTANT_BOOSTERS = setOf("shield", "oxygen")

/** Premium pack sizes for bronze…platinum. */
val PREMIUM_PACKS = intArrayOf(9, 18, 24, 36)

/** Every pack is laid out in 3 columns; long packs scroll. */
const val PACK_COLUMNS = 3

const val PREMIUM_XP = 2
const val PREMIUM_CRYSTALS = 3

private fun n(v: Long) = "%,d".format(v).replace(',', ' ')

val ACHIEVEMENTS = listOf(
    Achievement("first", "Первый контакт", S.SECTORS, listOf(1), 5) { "Решите первый сектор" },
    Achievement("vault_first", "Взломщик", S.VAULTS, listOf(1), 10) { "Вскройте первое хранилище" },
    Achievement("sectors", "Картограф", S.SECTORS, listOf(10, 100, 500, 2000), 5, "frame_gold") {
        "Решите ${n(it)} ${plural(it, "сектор", "сектора", "секторов")}" },
    Achievement("tiles", "Эхолот", S.TILES, listOf(1000, 10000, 50000, 200000), 8) {
        "Откройте ${n(it)} ${plural(it, "клетку", "клетки", "клеток")}" },
    Achievement("flags", "Смотритель буёв", S.FLAGS_OK, listOf(50, 300, 1500, 5000), 6) {
        "Поставьте ${n(it)} ${plural(it, "верный буй", "верных буя", "верных буёв")}" },
    Achievement("noflags", "Чистая интуиция", S.NO_FLAGS, listOf(5, 50, 250, 1000), 8, "frame_violet") {
        "Решите ${n(it)} ${plural(it, "сектор", "сектора", "секторов")} без буёв" },
    Achievement("distance", "Дальнее плавание", S.MAX_DIST, listOf(5, 10, 20, 40), 8) {
        "Решите сектор в ${n(it)} ${plural(it, "секторе", "секторах", "секторах")} от старта" },
    Achievement("level", "Опыт глубин", S.LEVEL, listOf(5, 10, 20, 35), 10, "frame_neon") { "Достигните ${n(it)} уровня" },
    Achievement("boosters", "Технарь", S.BOOSTERS, listOf(5, 25, 100, 300), 5) {
        "Используйте ${n(it)} ${plural(it, "бустер", "бустера", "бустеров")}" },
    Achievement("vaults", "Медвежатник", S.VAULTS, listOf(5, 25, 100, 300), 8) {
        "Вскройте ${n(it)} ${plural(it, "хранилище", "хранилища", "хранилищ")}" },
    Achievement("specials", "Охотник за аномалиями", S.SPECIALS, listOf(5, 30, 150, 500), 8) {
        "Решите ${n(it)} ${plural(it, "особый сектор", "особых сектора", "особых секторов")}" },
    Achievement("clean", "Хладнокровие", S.CLEAN, listOf(10, 25, 50, 100), 10) {
        "Решите ${n(it)} ${plural(it, "сектор", "сектора", "секторов")} подряд без подрывов" },
    Achievement("climb", "К поверхности", S.CLIMB, listOf(5, 15, 30, 60), 10) {
        "Поднимитесь на ${n(it)} ${plural(it, "сектор", "сектора", "секторов")} во «Всплытии»" },
    Achievement("hardcore", "Нервы из стали", S.BEST_HARDCORE, listOf(500, 2000, 6000, 15000), 10) {
        "Наберите ${n(it)} очков в режиме «Без права на ошибку»" },
    Achievement("timed", "Скоростной спуск", S.BEST_TIMED, listOf(300, 1000, 2500, 5000), 8) {
        "Наберите ${n(it)} очков в «Погружении на время»" },
    Achievement("streak", "Вахта", S.STREAK, listOf(3, 7, 14, 30), 8) {
        "Заходите в игру ${n(it)} ${plural(it, "день", "дня", "дней")} подряд" },
    Achievement("missions", "Исполнитель", S.MISSIONS, listOf(10, 50, 200, 500), 10) {
        "Выполните ${n(it)} ${plural(it, "задание", "задания", "заданий")}" },
    Achievement("chords", "Аккордеонист", S.CHORDS, listOf(50, 300, 1500, 5000), 5) {
        "Откройте соседей по цифре ${n(it)} ${plural(it, "раз", "раза", "раз")}" },
    Achievement("gifts", "Кладоискатель", S.CRYSTAL_TILES, listOf(50, 500, 2500, 10000), 8) {
        "Найдите ${n(it)} ${plural(it, "подарок", "подарка", "подарков")}" },
    Achievement("landmarks", "Исследователь глубин", S.LANDMARKS, listOf(1, 10, 40, 100), 10) {
        "Исследуйте ${n(it)} ${plural(it, "достопримечательность", "достопримечательности", "достопримечательностей")} (ракушки, рифы, корабли…)" },
    Achievement("chains", "Цепная реакция", S.CHAINS, listOf(3, 15, 60, 200), 10) {
        "Решите 3+ сектора за один ход ${n(it)} ${plural(it, "раз", "раза", "раз")}" },
    Achievement("score", "Рекордсмен", S.SCORE, listOf(5000, 50000, 250000, 1000000), 10) {
        "Наберите ${n(it)} очков за всё время" },
)

class MissionTemplate(val id: String, val stat: String, val text: (Long) -> String, val range: IntRange, val reward: Int)

val MISSIONS = listOf(
    MissionTemplate("tiles", S.TILES, { "Откройте $it ${plural(it, "клетку", "клетки", "клеток")}" }, 150..400, 6),
    MissionTemplate("sectors", S.SECTORS, { "Решите $it ${plural(it, "сектор", "сектора", "секторов")}" }, 4..12, 8),
    MissionTemplate("flags", S.FLAGS_OK, { "Поставьте $it ${plural(it, "верный буй", "верных буя", "верных буёв")}" }, 15..40, 7),
    MissionTemplate("noflag", S.NO_FLAGS, { "Решите $it ${plural(it, "сектор", "сектора", "секторов")} без буёв" }, 1..3, 10),
    MissionTemplate("crystal", S.CRYSTAL_TILES, { "Найдите $it ${plural(it, "подарок", "подарка", "подарков")} под клетками" }, 10..25, 6),
    MissionTemplate("special", S.SPECIALS, { "Решите $it ${plural(it, "особый сектор", "особых сектора", "особых секторов")}" }, 1..2, 10),
    MissionTemplate("chords", S.CHORDS, { "Откройте соседей по цифре $it ${plural(it, "раз", "раза", "раз")}" }, 10..30, 6),
    MissionTemplate("games", S.GAMES, { "Начните $it ${plural(it, "погружение", "погружения", "погружений")}" }, 1..2, 5),
    MissionTemplate("score", S.SCORE, { "Наберите $it ${plural(it, "очко", "очка", "очков")}" }, 300..900, 8),
    MissionTemplate("vault", S.VAULTS, { "Вскройте $it ${plural(it, "хранилище", "хранилища", "хранилищ")}" }, 1..1, 12),
    MissionTemplate("chain", S.CHAINS, { "Устройте $it ${plural(it, "цепную реакцию", "цепные реакции", "цепных реакций")} (3+ сектора за ход)" }, 1..2, 10),
    MissionTemplate("boost", S.BOOSTERS, { "Используйте $it ${plural(it, "бустер", "бустера", "бустеров")}" }, 1..2, 6),
)

const val DAILY_MISSIONS = 4

const val MISSION_BONUS = 15
val LOGIN_REWARDS = intArrayOf(3, 4, 5, 7, 8, 10, 20)

enum class Cat(val title: String) { BOOSTER("Бустеры"), THEME("Темы"), MINE("Мины"), FLAG("Буи"), NUMBERS("Цифры"), FRAME("Рамки") }

class Item(val id: String, val cat: Cat, val name: String, val price: Int, val level: Int = 1, val desc: String = "")

// ids are kept from older versions so saved inventories carry over; names and effects changed
val BOOSTERS = listOf(
    Item("sonar", Cat.BOOSTER, "Перископ", 35, desc = "Луч перископа сужается к случайной безопасной клетке сектора и открывает её"),
    Item("echo", Cat.BOOSTER, "Эхолот", 90, desc = "Волна от центра сектора открывает безопасные клетки в случайном квадрате 3×3"),
    Item("trawl", Cat.BOOSTER, "Трал", 50, desc = "Выберите клетку и направление — буи встанут на все мины в ней и 3 клетках дальше"),
    Item("flash", Cat.BOOSTER, "Сонар", 70, desc = "Луч сонара обходит сектор по кругу и на 2 секунды показывает все мины"),
    Item("shield", Cat.BOOSTER, "Щит", 120, desc = "Следующий подрыв не сработает: сектор не заблокируется, серия сохранится"),
    Item("defuse", Cat.BOOSTER, "Разминирование", 64, desc = "Снимает блокировку с сектора"),
    Item("lockpick", Cat.BOOSTER, "Батискаф", 150, desc = "Вскрывает хранилище, не дожидаясь соседних секторов"),
    Item("oxygen", Cat.BOOSTER, "Кислород", 40, desc = "+30 секунд в «Погружении на время»"),
    Item("solve", Cat.BOOSTER, "Торпеда", 350, desc = "Взрыв в центре сектора уничтожает все мины — сектор считается решённым"),
)

val ITEMS = BOOSTERS + listOf(
    Item("theme_deep", Cat.THEME, "Глубина", 0),
    Item("theme_kelp", Cat.THEME, "Ламинарии", 50, 2),
    Item("theme_coral", Cat.THEME, "Коралловый риф", 60, 3),
    Item("theme_arctic", Cat.THEME, "Арктика", 70, 4),
    Item("theme_volcano", Cat.THEME, "Чёрные курильщики", 90, 5),
    Item("theme_neon", Cat.THEME, "Биолюминесценция", 120, 7),
    Item("mine_naval", Cat.MINE, "Морская мина", 0),
    Item("mine_urchin", Cat.MINE, "Морской ёж", 40, 2),
    Item("mine_orb", Cat.MINE, "Глубинная сфера", 45, 3),
    Item("mine_shell", Cat.MINE, "Ракушка", 55, 5),
    Item("flag_buoy", Cat.FLAG, "Буй", 0),
    Item("flag_pennant", Cat.FLAG, "Вымпел", 35, 2),
    Item("flag_star", Cat.FLAG, "Морская звезда", 45, 3),
    Item("flag_beacon", Cat.FLAG, "Маяк", 55, 5),
    Item("num_sonar", Cat.NUMBERS, "Сонар", 0),
    Item("num_pastel", Cat.NUMBERS, "Пастель", 30, 2),
    Item("num_mono", Cat.NUMBERS, "Монохром", 30, 3),
    Item("num_heat", Cat.NUMBERS, "Термоклин", 40, 4),
    Item("frame_none", Cat.FRAME, "Без рамки", 0),
    Item("frame_teal", Cat.FRAME, "Бирюзовая", 30),
    Item("frame_coral", Cat.FRAME, "Коралловая", 40, 3),
    Item("frame_gold", Cat.FRAME, "Золотая (платина «Картограф»)", -1),
    Item("frame_violet", Cat.FRAME, "Фиолетовая (платина «Чистая интуиция»)", -1),
    Item("frame_neon", Cat.FRAME, "Неоновая (платина «Опыт глубин»)", -1),
)

/** Avatar emoji and the level that unlocks it. */
val AVATARS = listOf("🐙" to 1, "🐠" to 1, "🦀" to 2, "🐡" to 3, "🦑" to 4, "🐢" to 5, "🪼" to 6, "🐳" to 8, "🦈" to 10, "🐋" to 12, "🧜" to 15, "🔱" to 20)
