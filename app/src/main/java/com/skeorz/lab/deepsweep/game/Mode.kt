package com.skeorz.lab.deepsweep.game

/** Endless-mode difficulty: radii (in sectors) of the rings capping numbers at 3, 4, 5 and 7. */
enum class Difficulty(val title: String, val rings: IntArray, val xp: Double) {
    EASY("Лёгкий", intArrayOf(6, 9, 15, 30), 0.5),
    NORMAL("Средний", intArrayOf(4, 6, 10, 20), 1.0),
    HARD("Сложный", intArrayOf(3, 4, 7, 14), 1.5),
    EXTREME("Очень сложный", intArrayOf(2, 3, 5, 10), 1.8),
}

enum class Mode(
    val title: String,
    val desc: String,
    val unlockLevel: Int,
    val baseDensity: Double = 0.10,
    val growth: Double = 0.006,
    val maxDensity: Double = 0.21,
    val lockOnMine: Boolean = true,
    val hardcore: Boolean = false,
    val timeLimit: Int = 0,
    val climb: Boolean = false,
    val brutalMines: IntRange? = null,
    val scoreMul: Double = 1.0,
    val gifts: Boolean = true,
) {
    TUTORIAL("Обучение", "Пять шагов, чтобы освоить сонар.", 1, baseDensity = 0.11, growth = 0.0, gifts = false),
    ENDLESS("Бескрайняя глубина", "Бесконечное поле. Ошибка блокирует сектор, но игра продолжается.", 1),
    TIMED("Погружение на время", "5 минут, чтобы набрать максимум очков. Мина отнимает 15 секунд.", 2,
        baseDensity = 0.12, timeLimit = 300, scoreMul = 1.3),
    CLIMB("Всплытие", "Узкий коридор в три сектора — поднимайтесь как можно выше.", 3,
        baseDensity = 0.11, growth = 0.01, maxDensity = 0.24, climb = true, scoreMul = 1.2),
    HARDCORE("Без права на ошибку", "Одна мина — и погружение окончено. Очки ×2.", 4,
        lockOnMine = false, hardcore = true, scoreMul = 2.0),
    BRUTAL("Минное ущелье", "В каждом секторе от 12 до 17 мин. Для настоящих профи.", 6,
        brutalMines = 12..17, scoreMul = 2.5),
}
