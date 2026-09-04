package mobi.tomo.reversi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 盤面の描画とタッチ入力を担当する View。
 *
 * 現状は Java 版からそのまま移植した状態で、ゲーム進行のロジックが [onDraw] の中にある。
 * 盤面モデルと CPU 思考への分離はフェーズ4以降で行う。
 */
class ReversiView(context: Context) : View(context) {

    private val paint = Paint().apply {
        setARGB(200, 255, 255, 255)
        textSize = 30f
    }

    // 画像読み込み
    private val imgBoard = BitmapFactory.decodeResource(resources, R.drawable.board)
    private val imgBlack = BitmapFactory.decodeResource(resources, R.drawable.black)
    private val imgWhite = BitmapFactory.decodeResource(resources, R.drawable.white)
    private val imgLight = BitmapFactory.decodeResource(resources, R.drawable.light)

    private val board = IntArray(100)
    private val placeMap = IntArray(100)
    private var page = TITLE
    private var turn = PLAYER
    private var place = 0
    private var playerColor = BLACK

    // 描画処理
    override fun onDraw(c: Canvas) {
        // ボードを表示
        c.drawBitmap(imgBoard, 0f, 0f, paint)
        for (i in 11..88) {
            if (playerColor == BLACK) {
                if (board[i] == PLAYER) c.drawBitmap(imgBlack, cellX(i), cellY(i), paint)
                if (board[i] == COM) c.drawBitmap(imgWhite, cellX(i), cellY(i), paint)
            } else {
                if (board[i] == PLAYER) c.drawBitmap(imgWhite, cellX(i), cellY(i), paint)
                if (board[i] == COM) c.drawBitmap(imgBlack, cellX(i), cellY(i), paint)
            }
        }
        when (page) {
            TITLE -> Unit

            TURN -> {
                // ページ移動
                page = turn
                invalidate()
            }

            PLAYER -> {
                makePlaceMap(PLAYER)
                // 置ける所を表示
                for (i in 11..88) {
                    if (placeMap[i] > 0) c.drawBitmap(imgLight, cellX(i), cellY(i), paint)
                }
            }

            COM -> {
                makePlaceMap(COM)
                // 置ける所を表示
                for (i in 11..88) {
                    if (placeMap[i] > 0) c.drawBitmap(imgLight, cellX(i), cellY(i), paint)
                }
            }

            REVERS -> {
                // 置いて裏返す
                reverse(turn, place)
                // ページ移動
                page = CONTROL
                invalidate()
            }

            CONTROL -> {
                // ターンを交代
                turn = if (turn == PLAYER) COM else PLAYER
                // ページ移動
                page = when {
                    makePlaceMap(PLAYER) && makePlaceMap(COM) -> RESULT
                    makePlaceMap(turn) -> PASS
                    else -> TURN
                }
                invalidate()
            }

            PASS -> {
                c.drawText(context.getString(R.string.pass), 200f, 600f, paint)
                // ターンを交代
                turn = if (turn == PLAYER) COM else PLAYER
                // ページ移動
                page = TURN
                invalidate()
            }

            RESULT -> {
                c.drawText(context.getString(R.string.result), 200f, 550f, paint)
                c.drawLine(50f, 560f, 430f, 560f, paint)
                c.drawText(context.getString(R.string.black_count, count(BLACK)), 100f, 650f, paint)
                c.drawText(context.getString(R.string.white_count, count(WHITE)), 100f, 700f, paint)
            }
        }
    }

    // タッチ入力処理
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(me: MotionEvent): Boolean {
        val padX = (me.x / CELL_SIZE).toInt()
        val padY = (me.y / CELL_SIZE).toInt()

        // タッチされた時
        if (me.action == MotionEvent.ACTION_DOWN) {
            when (page) {
                TITLE -> {
                    // ゲームの初期化
                    board.fill(0)
                    for (i in 0..9) board[i] = -1
                    for (i in 1..8) board[i * 10] = -1
                    for (i in 1..8) board[i * 10 + 9] = -1
                    for (i in 0..9) board[i + 90] = -1
                    // 黒（先攻）を選択
                    if (padX in 2..3 && padY in 7..8) {
                        playerColor = BLACK
                        board[44] = COM
                        board[45] = PLAYER
                        board[54] = PLAYER
                        board[55] = COM
                        turn = PLAYER
                        makePlaceMap(turn)
                        // ページ移動
                        page = TURN
                        invalidate()
                    }
                    // 白（後攻）を選択
                    if (padX in 6..7 && padY in 7..8) {
                        playerColor = WHITE
                        board[44] = PLAYER
                        board[45] = COM
                        board[54] = COM
                        board[55] = PLAYER
                        turn = COM
                        makePlaceMap(turn)
                        // ページ移動
                        page = TURN
                        invalidate()
                    }
                }

                PLAYER, COM -> {
                    if (placeMap[padX + padY * 10] > 0) {
                        place = padX + padY * 10
                        // ページ移動
                        page = REVERS
                        invalidate()
                    }
                }

                RESULT -> {
                    // ページ移動
                    page = TITLE
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(me)
    }

    // 置いて裏返す
    private fun reverse(myCoin: Int, p: Int) {
        val yourCoin = if (myCoin == PLAYER) COM else PLAYER

        board[p] = myCoin
        for (i in 0..7) {
            if (board[p + MOVE[i]] == yourCoin) {
                for (j in 2..7) {
                    if (board[p + MOVE[i] * j] == myCoin) {
                        for (k in 1 until j) {
                            board[p + MOVE[i] * k] = myCoin
                        }
                        break
                    } else if (board[p + MOVE[i] * j] != yourCoin) {
                        break
                    }
                }
            }
        }
    }

    /** どこに置けるか？ 置ける場所が一つも無い（＝パス）ときに true を返す。 */
    private fun makePlaceMap(myCoin: Int): Boolean {
        val yourCoin = if (myCoin == PLAYER) COM else PLAYER
        var pass = true

        for (p in 0..99) {
            placeMap[p] = 0
            if (p > 0 && board[p] == 0) {
                for (i in 0..7) {
                    if (board[p + MOVE[i]] == yourCoin) {
                        for (j in 2..7) {
                            if (board[p + MOVE[i] * j] == myCoin) {
                                placeMap[p] += j - 1
                                pass = false
                                break
                            } else if (board[p + MOVE[i] * j] != yourCoin) {
                                break
                            }
                        }
                    }
                }
            }
        }
        return pass
    }

    // 石を数える
    private fun count(color: Int): Int {
        val coin = if (playerColor == color) PLAYER else COM
        return board.count { it == coin }
    }

    private fun cellX(index: Int) = (CELL_SIZE * (index % 10)).toFloat()

    private fun cellY(index: Int) = (CELL_SIZE * (index / 10)).toFloat()

    private companion object {
        const val TITLE = 0
        const val PLAYER = 1
        const val COM = 2
        const val TURN = 3
        const val REVERS = 4
        const val CONTROL = 5
        const val PASS = 6
        const val RESULT = 7

        const val BLACK = 0
        const val WHITE = 1

        /** 1 マスの大きさ（px）。フェーズ2で画面サイズから算出するよう直す。 */
        const val CELL_SIZE = 48

        val MOVE = intArrayOf(-11, -10, -9, -1, 1, 9, 10, 11)
    }
}
