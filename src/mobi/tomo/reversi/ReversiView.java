package mobi.tomo.reversi;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

class ReversiView extends View {
    private Paint paint = new Paint();

	 //画像読み込み
    private Resources res = this.getContext().getResources();
    private final Bitmap IMG_BOARD = BitmapFactory.decodeResource(res, R.drawable.board);
    private final Bitmap IMG_BLACK = BitmapFactory.decodeResource(res, R.drawable.black);
    private final Bitmap IMG_WHITE = BitmapFactory.decodeResource(res, R.drawable.white);
    private final Bitmap IMG_LIGHT = BitmapFactory.decodeResource(res, R.drawable.light);

    private final int TITLE = 0;
    private final int PLAYER = 1;
    private final int COM = 2;
    private final int TURN = 3;
    private final int REVERS = 4;
    private final int CONTROL = 5;
    private final int PASS = 6;
    private final int RESULT = 7;

    private int[] board = new int[100];
    private int page = TITLE;
    private int turn;
    private int place;

    String action = "";
    
    public ReversiView(Context context) {
        super(context);
    	//タッチイベント取得
    	//setTouchEventsEnabled(true);
      }
    
    //描写処理
    @Override
    public void onDraw(Canvas c) {
        int i;

    	//ボードを表示
        c.drawBitmap(IMG_BOARD, 0, 0, paint);
        for(i=11;i<=88;i++) {
            if(board[i]==PLAYER) c.drawBitmap(IMG_BLACK, 48*(i%10), 48*(i/10), paint);
            if(board[i]==COM) c.drawBitmap(IMG_WHITE, 48*(i%10), 48*(i/10), paint);
        }
        switch(page) {
        case TITLE:
        	action = "TITLE2";
            break;
        case TURN:
            //ページ移動
            page = turn;
            invalidate();
            break;
        case PLAYER:
            break;
        case COM:
            break;
        case REVERS:
            //置いて裏返す
            reverse(turn, place);
           //ページ移動
            page = CONTROL;
            invalidate();
           break;
        case CONTROL:
            //ターンを交代
            if(turn==PLAYER) turn = COM;
            else turn = PLAYER;
            //ページ移動
            page = TURN;
            invalidate();
           break;
        case PASS:
            break;
        case RESULT:
            break;
        }
    }
    
    //タッチ入力処理
    @Override
    public boolean onTouchEvent(MotionEvent me) {
    	
        int i;
        int padX = (int)(me.getX()/48);
        int padY = (int)(me.getY()/48);
        
        
       //タッチされた時
        if(me.getAction()==MotionEvent.ACTION_DOWN) {
        	Log.d("tag","たっちしました");
            switch(page) {
            case TITLE:
                //ゲームの初期化
                for(i=0;i<100;i++) board[i] = 0;
                for(i=0;i<10;i++) board[i] = -1;
                for(i=1;i<9;i++) board[i*10] = -1;
                for(i=1;i<9;i++) board[i*10+9] = -1;
                for(i=0;i<10;i++) board[i+90] = -1;
                board[44] = COM;
                board[45] = PLAYER;
                board[54] = PLAYER;
                board[55] = COM;
                turn = PLAYER;
                //ページ移動
                page = TURN;

                invalidate();
                break;
            case PLAYER:
                place = padX+padY*10;
               //ページ移動
                page = REVERS;
                invalidate();
            	action = "PLAYER";
              break;
            case COM:
                place = padX+padY*10;
               //ページ移動
                page = REVERS;
                invalidate();
            	action = "COM";
               break;
            case PASS:
            	action = "PASS";
                break;
            case RESULT:
            	action = "RESULT";
                break;
            }
            Log.v("MotionEvent",
                    "action = " + page + ", " +
                    "x = " + String.valueOf(me.getX()) + ", " +
                    "y = " + String.valueOf(me.getY()));

        }
        return super.onTouchEvent(me);
    }
    //置いて裏返す
    void reverse(int myCoin, int p) {
        int yourCoin = PLAYER;
        
        if(myCoin==PLAYER) yourCoin = COM;

        board[p] = myCoin;
    }
}
