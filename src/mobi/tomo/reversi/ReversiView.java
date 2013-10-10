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
    private final int[] MOVE = {-11, -10, -9, -1, 1, 9, 10, 11};
    private final int BLACK = 0;
    private final int WHITE = 1;

    private int[] board = new int[100];
    private int page = TITLE;
    private int turn;
    private int[] placeMap = new int[100];
    private int place;
    private int playerColor;

    String action = "";
    
    public ReversiView(Context context) {
        super(context);
        paint.setARGB(200,255,255,255);
        paint.setTextSize(30);
      }
    
    //描写処理
    @Override
    public void onDraw(Canvas c) {
        int i;

    	//ボードを表示
        c.drawBitmap(IMG_BOARD, 0, 0, paint);
        for(i=11;i<=88;i++) {
            if(playerColor==BLACK) {
                if(board[i]==PLAYER) c.drawBitmap(IMG_BLACK, 48*(i%10), 48*(i/10), paint);
                if(board[i]==COM) c.drawBitmap(IMG_WHITE, 48*(i%10), 48*(i/10), paint);
            } else {
                if(board[i]==PLAYER) c.drawBitmap(IMG_WHITE, 48*(i%10), 48*(i/10), paint);
                if(board[i]==COM) c.drawBitmap(IMG_BLACK, 48*(i%10), 48*(i/10), paint);
            }
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
        	makePlaceMap(PLAYER);
            //置ける所を表示
            for(i=11;i<=88;i++) {
                if(placeMap[i]>0) c.drawBitmap(IMG_LIGHT, 48*(i%10), 48*(i/10), paint);
            }
            break;
        case COM:
        	makePlaceMap(COM);
            //置ける所を表示
            for(i=11;i<=88;i++) {
                if(placeMap[i]>0) c.drawBitmap(IMG_LIGHT, 48*(i%10), 48*(i/10), paint);
            }
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
            if(makePlaceMap(PLAYER)==true && makePlaceMap(COM)==true) page = RESULT;
            else if(makePlaceMap(turn)==true) page = PASS;
            else page = TURN;
            invalidate();
           break;
        case PASS:
            c.drawText("パス", 200, 600, paint);
            //ターンを交代
            if(turn==PLAYER) turn = COM;
            else turn = PLAYER;
            //ページ移動
            page = TURN;
            invalidate();
            break;
        case RESULT:
            c.drawText("結果", 200, 550, paint);
            c.drawLine(50,560,430,560,paint);
            c.drawText("黒　"+count(BLACK)+"　個", 100, 650, paint);
            c.drawText("白　"+count(WHITE)+"　個", 100, 700, paint);
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
                //黒（先攻）を選択
                if(2<=padX && padX<=3 && 7<=padY && padY<=8){
                    playerColor = BLACK;
                    board[44] = COM;
                    board[45] = PLAYER;
                    board[54] = PLAYER;
                    board[55] = COM;
                    turn = PLAYER;
                    makePlaceMap(turn);
                    //ページ移動
                    page = TURN;
                    invalidate();
                }
                if(6<=padX && padX<=7 && 7<=padY && padY<=8){
                    playerColor = WHITE;
                    board[44] = PLAYER;
                    board[45] = COM;
                    board[54] = COM;
                    board[55] = PLAYER;
                    turn = COM;
                    makePlaceMap(turn);
                    //ページ移動
                    page = TURN;
                    invalidate();
                }
               break;
            case PLAYER:
            	if(placeMap[padX+padY*10]>0) {
	                place = padX+padY*10;
	               //ページ移動
	                page = REVERS;
	                invalidate();
            	}
              break;
            case COM:
            	if(placeMap[padX+padY*10]>0) {
	                place = padX+padY*10;
	               //ページ移動
	                page = REVERS;
	                invalidate();
            	}
               break;
            case PASS:
            	action = "PASS";
                break;
            case RESULT:
                //ページ移動
            	page = TITLE;
                invalidate();
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
        int i, j, k;
        
        if(myCoin==PLAYER) yourCoin = COM;

        board[p] = myCoin;
	        for(i=0;i<8;i++) {
	            if(board[p+MOVE[i]]==yourCoin) {
	                for(j=2;j<8;j++) {
	                    if(board[p+MOVE[i]*j]==myCoin) {
	                        for(k=1;k<j;k++) {
	                            board[p+MOVE[i]*k] = myCoin;
	                        }
	                        break;
	                    } else if(board[p+MOVE[i]*j]==yourCoin) {
	                    } else {
	                        break;
	                    }
	                }
	            }
	        }
    }
    
    //どこに置けるか？
    public boolean makePlaceMap(int myCoin) {
        int yourCoin = PLAYER;
        int i, j;
        boolean pass = true;
        
        if(myCoin==PLAYER) yourCoin = COM;
        
        for(int p=0;p<100;p++) {
            placeMap[p] = 0;
            if(0<p && p<100 && board[p]==0) {
                for(i=0;i<8;i++) {
                    if(board[p+MOVE[i]]==yourCoin) {
                        for(j=2;j<8;j++) {
                            if(board[p+MOVE[i]*j]==myCoin) {
                                placeMap[p] += j-1;
                                pass = false;
                                break;
                            } else if(board[p+MOVE[i]*j]==yourCoin) {
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return pass;
    }

    //石を数える
    public int count(int color) {
    	int count = 0;

        if(playerColor==color) {
        	for(int i=0;i<100;i++) {
        		if(board[i]==PLAYER) count++;
        	}
        } else {
        	for(int i=0;i<100;i++) {
        		if(board[i]==COM) count++;
        	}
        }
    	
    	return count;
    }

}
