package mobi.tomo.reversi.game

/** 石の色。 */
enum class Disc(internal val code: Int) {
    BLACK(1),
    WHITE(2),
    ;

    val opposite: Disc
        get() = if (this == BLACK) WHITE else BLACK
}
