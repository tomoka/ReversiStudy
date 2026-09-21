package mobi.tomo.reversi.game

/**
 * CPU の強さ。5 段階。
 *
 * 弱い段階は読みを浅くするだけでなく、ときどきわざとランダムな手を選ぶ。
 * 浅い読みでも隅を優先する評価は効くため、それだけでは初心者には手強すぎるため。
 *
 * 読みの深さはすべて偶数にしてある。奇数手で打ち切ると自分が打った直後の
 * 局面で評価することになり、相手の返しを織り込めないぶん判断が甘くなる。
 * 実測でも depth 7 は depth 6 に勝ち越せなかった。
 */
enum class Difficulty(
    /** 読みの深さ（手）。 */
    val depth: Int,

    /** 空きマスがこの数以下になったら最後まで読み切る。0 なら読み切らない。 */
    val endgameEmpties: Int,

    /** この割合で、最善手ではなくランダムな手を選ぶ。 */
    val randomness: Float,
) {
    BEGINNER(depth = 1, endgameEmpties = 0, randomness = 0.7f),
    EASY(depth = 2, endgameEmpties = 4, randomness = 0.3f),
    NORMAL(depth = 4, endgameEmpties = 8, randomness = 0f),
    HARD(depth = 6, endgameEmpties = 10, randomness = 0f),
    EXPERT(depth = 8, endgameEmpties = 12, randomness = 0f),
    ;

    /** 1 から 5。画面に出す番号。 */
    val level: Int
        get() = ordinal + 1

    companion object {
        val DEFAULT = NORMAL

        /** 1 から 5 の番号から。範囲外なら [DEFAULT]。 */
        fun ofLevel(level: Int): Difficulty = entries.getOrElse(level - 1) { DEFAULT }

        fun ofName(name: String?): Difficulty = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
