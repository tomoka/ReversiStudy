package mobi.tomo.reversi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

/**
 * 盤面の描画とタッチ入力を担当する View。
 *
 * 盤は画像ではなく Canvas で描き、マスの大きさは View の実寸から毎回算出する。
 * 描画とタッチ判定が同じ [cell] / [boardLeft] / [boardTop] を見るため、
 * 画面の密度やサイズが変わってもズレない。
 *
 * ゲーム進行のロジックはまだこの View の中にある。盤面モデルと CPU 思考への
 * 分離はフェーズ3以降で行う。
 */
class ReversiView(context: Context) : View(context) {

    private val board = IntArray(100)
    private val placeMap = IntArray(100)

    private var page = TITLE
    private var turn = PLAYER
    private var playerColor = BLACK

    /** 盤の一辺（px）。0 のうちはまだ採寸できていない。 */
    private var boardSide = 0f
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f

    private var statusBaseline = 0f
    private var scoreBaseline = 0f

    private val blackButton = RectF()
    private val whiteButton = RectF()
    private val passBox = RectF()

    private val boardPaint = fillPaint(R.color.board_green)
    private val blackPaint = fillPaint(R.color.disc_black)
    private val whitePaint = fillPaint(R.color.disc_white)
    private val hintPaint = fillPaint(R.color.hint)
    private val scrimPaint = fillPaint(R.color.scrim)
    private val overlayPaint = fillPaint(R.color.overlay)
    private val buttonPaint = fillPaint(R.color.button_fill)
    private val starPaint = fillPaint(R.color.board_line)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_line)
        style = Paint.Style.STROKE
    }
    private val buttonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.button_stroke)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val discEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.board_line)
        style = Paint.Style.STROKE
    }

    private val titlePaint = textPaint(R.color.text_primary, sp(30f))
    private val bodyPaint = textPaint(R.color.text_primary, sp(18f))
    private val labelPaint = textPaint(R.color.text_secondary, sp(14f))

    /** パス表示を一定時間見せてから手番を戻す。 */
    private val passRunnable = Runnable {
        turn = opponent(turn)
        makePlaceMap(turn)
        page = PLAY
        invalidate()
    }

    init {
        resetBoard()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val availableWidth = (w - paddingLeft - paddingRight).toFloat()
        val availableHeight = (h - paddingTop - paddingBottom).toFloat()
        // 余白は画面に対する割合で頭打ちにする。小さな画面でも盤が必ず残るようにするため。
        val header = min(dp(76f), availableHeight * 0.14f)
        val footer = min(dp(40f), availableHeight * 0.07f)
        val margin = min(dp(12f), availableWidth * 0.03f)

        boardSide = max(
            0f,
            min(availableWidth - margin * 2f, availableHeight - header - footer - margin * 2f),
        )
        cell = boardSide / 8f
        boardLeft = paddingLeft + (availableWidth - boardSide) / 2f
        boardTop = paddingTop + header +
            max(0f, (availableHeight - header - footer - boardSide) / 2f)

        statusBaseline = boardTop - header * 0.45f
        scoreBaseline = boardTop - header * 0.14f

        linePaint.strokeWidth = max(dp(1f), cell * 0.02f)
        discEdgePaint.strokeWidth = max(dp(0.5f), cell * 0.02f)

        // 文字は sp を基本にしつつ、盤より大きくならないよう頭打ちにする。
        titlePaint.textSize = min(sp(30f), boardSide * 0.11f)
        bodyPaint.textSize = min(sp(18f), boardSide * 0.075f)
        labelPaint.textSize = min(sp(14f), boardSide * 0.055f)

        val buttonHeight = boardSide * 0.14f
        val buttonTop = boardTop + boardSide * 0.58f
        blackButton.set(
            boardLeft + boardSide * 0.06f,
            buttonTop,
            boardLeft + boardSide * 0.47f,
            buttonTop + buttonHeight,
        )
        whiteButton.set(
            boardLeft + boardSide * 0.53f,
            buttonTop,
            boardLeft + boardSide * 0.94f,
            buttonTop + buttonHeight,
        )
        val passHeight = boardSide * 0.2f
        passBox.set(
            boardLeft + boardSide * 0.2f,
            boardTop + (boardSide - passHeight) / 2f,
            boardLeft + boardSide * 0.8f,
            boardTop + (boardSide + passHeight) / 2f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (boardSide <= 0f) return

        drawBoard(canvas)
        when (page) {
            TITLE -> drawTitle(canvas)
            PLAY -> {
                drawStones(canvas)
                drawHints(canvas)
                drawStatus(canvas)
            }
            PASS -> {
                drawStones(canvas)
                drawStatus(canvas)
                drawPass(canvas)
            }
            RESULT -> {
                drawStones(canvas)
                drawResult(canvas)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event)

        when (page) {
            TITLE -> when {
                blackButton.contains(event.x, event.y) -> startGame(BLACK)
                whiteButton.contains(event.x, event.y) -> startGame(WHITE)
            }
            PLAY -> {
                val index = indexAt(event.x, event.y)
                if (index != NO_INDEX && placeMap[index] > 0) {
                    reverse(turn, index)
                    advanceTurn()
                }
            }
            RESULT -> backToTitle()
            PASS -> Unit
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(passRunnable)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------------ 進行

    private fun startGame(color: Int) {
        resetBoard()
        playerColor = color
        if (color == BLACK) {
            board[44] = COM
            board[45] = PLAYER
            board[54] = PLAYER
            board[55] = COM
            turn = PLAYER
        } else {
            board[44] = PLAYER
            board[45] = COM
            board[54] = COM
            board[55] = PLAYER
            turn = COM
        }
        page = PLAY
        makePlaceMap(turn)
        invalidate()
    }

    /** 手番を交代し、パスと終局を判定する。 */
    private fun advanceTurn() {
        turn = opponent(turn)
        val currentCannotMove = makePlaceMap(turn)
        val opponentCannotMove = makePlaceMap(opponent(turn))
        when {
            currentCannotMove && opponentCannotMove -> page = RESULT
            currentCannotMove -> {
                makePlaceMap(turn)
                page = PASS
                removeCallbacks(passRunnable)
                postDelayed(passRunnable, PASS_DISPLAY_MILLIS)
            }
            else -> {
                makePlaceMap(turn)
                page = PLAY
            }
        }
        invalidate()
    }

    private fun backToTitle() {
        removeCallbacks(passRunnable)
        resetBoard()
        page = TITLE
        invalidate()
    }

    private fun resetBoard() {
        board.fill(EMPTY)
        placeMap.fill(0)
        for (i in 0..9) {
            board[i] = WALL
            board[i + 90] = WALL
        }
        for (i in 1..8) {
            board[i * 10] = WALL
            board[i * 10 + 9] = WALL
        }
    }

    // ------------------------------------------------------------------ 描画

    private fun drawBoard(canvas: Canvas) {
        canvas.drawRect(boardLeft, boardTop, boardLeft + boardSide, boardTop + boardSide, boardPaint)
        for (i in 0..8) {
            val x = boardLeft + cell * i
            canvas.drawLine(x, boardTop, x, boardTop + boardSide, linePaint)
            val y = boardTop + cell * i
            canvas.drawLine(boardLeft, y, boardLeft + boardSide, y, linePaint)
        }
        for (col in intArrayOf(2, 6)) {
            for (row in intArrayOf(2, 6)) {
                canvas.drawCircle(
                    boardLeft + cell * col,
                    boardTop + cell * row,
                    cell * 0.07f,
                    starPaint,
                )
            }
        }
    }

    private fun drawStones(canvas: Canvas) {
        val radius = cell * 0.4f
        for (index in 11..88) {
            val seat = board[index]
            if (seat != PLAYER && seat != COM) continue
            val paint = if (colorOf(seat) == BLACK) blackPaint else whitePaint
            val x = centerX(index)
            val y = centerY(index)
            canvas.drawCircle(x, y, radius, paint)
            canvas.drawCircle(x, y, radius, discEdgePaint)
        }
    }

    private fun drawHints(canvas: Canvas) {
        for (index in 11..88) {
            if (placeMap[index] > 0) {
                canvas.drawCircle(centerX(index), centerY(index), cell * 0.13f, hintPaint)
            }
        }
    }

    private fun drawStatus(canvas: Canvas) {
        val centerX = boardLeft + boardSide / 2f
        val turnText = if (turnColor() == BLACK) R.string.turn_black else R.string.turn_white
        canvas.drawText(context.getString(turnText), centerX, statusBaseline, bodyPaint)
        canvas.drawText(
            context.getString(R.string.score, count(BLACK), count(WHITE)),
            centerX,
            scoreBaseline,
            labelPaint,
        )
    }

    private fun drawTitle(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val centerX = boardLeft + boardSide / 2f
        canvas.drawText(
            context.getString(R.string.app_name),
            centerX,
            boardTop + boardSide * 0.3f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.choose_side),
            centerX,
            boardTop + boardSide * 0.44f,
            bodyPaint,
        )
        drawChoiceButton(canvas, blackButton, BLACK, context.getString(R.string.choose_black))
        drawChoiceButton(canvas, whiteButton, WHITE, context.getString(R.string.choose_white))
    }

    private fun drawChoiceButton(canvas: Canvas, rect: RectF, color: Int, label: String) {
        val corner = rect.height() / 4f
        canvas.drawRoundRect(rect, corner, corner, buttonPaint)
        canvas.drawRoundRect(rect, corner, corner, buttonStrokePaint)

        val discX = rect.left + rect.height() * 0.5f
        canvas.drawCircle(
            discX,
            rect.centerY(),
            rect.height() * 0.28f,
            if (color == BLACK) blackPaint else whitePaint,
        )
        canvas.drawText(
            label,
            (discX + rect.right) / 2f,
            rect.centerY() + labelPaint.textSize * 0.36f,
            labelPaint,
        )
    }

    private fun drawPass(canvas: Canvas) {
        val corner = passBox.height() / 4f
        canvas.drawRoundRect(passBox, corner, corner, overlayPaint)
        canvas.drawText(
            context.getString(R.string.pass),
            passBox.centerX(),
            passBox.centerY() + titlePaint.textSize * 0.36f,
            titlePaint,
        )
    }

    private fun drawResult(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        val centerX = boardLeft + boardSide / 2f
        val black = count(BLACK)
        val white = count(WHITE)
        canvas.drawText(
            context.getString(R.string.result),
            centerX,
            boardTop + boardSide * 0.26f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.black_count, black),
            centerX,
            boardTop + boardSide * 0.42f,
            bodyPaint,
        )
        canvas.drawText(
            context.getString(R.string.white_count, white),
            centerX,
            boardTop + boardSide * 0.52f,
            bodyPaint,
        )
        val winner = when {
            black > white -> R.string.winner_black
            white > black -> R.string.winner_white
            else -> R.string.draw
        }
        canvas.drawText(
            context.getString(winner),
            centerX,
            boardTop + boardSide * 0.68f,
            titlePaint,
        )
        canvas.drawText(
            context.getString(R.string.tap_to_restart),
            centerX,
            boardTop + boardSide * 0.82f,
            labelPaint,
        )
    }

    // ------------------------------------------------------------ 座標の変換

    private fun centerX(index: Int) = boardLeft + cell * (index % 10 - 0.5f)

    private fun centerY(index: Int) = boardTop + cell * (index / 10 - 0.5f)

    /** タッチ座標を盤上のマス番号へ。盤の外なら [NO_INDEX]。 */
    private fun indexAt(x: Float, y: Float): Int {
        if (cell <= 0f || x < boardLeft || y < boardTop) return NO_INDEX
        val col = ((x - boardLeft) / cell).toInt() + 1
        val row = ((y - boardTop) / cell).toInt() + 1
        if (col !in 1..8 || row !in 1..8) return NO_INDEX
        return row * 10 + col
    }

    // -------------------------------------------------------------- ルール

    /** 置いて裏返す。 */
    private fun reverse(myCoin: Int, p: Int) {
        val yourCoin = opponent(myCoin)

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
        val yourCoin = opponent(myCoin)
        var pass = true

        for (p in 0..99) {
            placeMap[p] = 0
            if (p > 0 && board[p] == EMPTY) {
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

    /** 石を数える。 */
    private fun count(color: Int): Int {
        val seat = if (playerColor == color) PLAYER else COM
        return board.count { it == seat }
    }

    private fun opponent(seat: Int) = if (seat == PLAYER) COM else PLAYER

    private fun colorOf(seat: Int) = if (seat == PLAYER) playerColor else 1 - playerColor

    private fun turnColor() = colorOf(turn)

    // -------------------------------------------------------------- 小道具

    private fun fillPaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        style = Paint.Style.FILL
    }

    private fun textPaint(colorRes: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        textSize = size
        textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val TITLE = 0
        const val PLAY = 1
        const val PASS = 2
        const val RESULT = 3

        const val EMPTY = 0
        const val PLAYER = 1
        const val COM = 2
        const val WALL = -1

        const val BLACK = 0
        const val WHITE = 1

        const val NO_INDEX = -1
        const val PASS_DISPLAY_MILLIS = 1200L

        val MOVE = intArrayOf(-11, -10, -9, -1, 1, 9, 10, 11)
    }
}
