package blacknet.mygame;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

public class GamePanel extends SurfaceView implements SurfaceHolder.Callback {

    public static final int WIDTH = 856;
    public static final int HEIGHT = 480;
    public static final int MOVESPEED = -5;
    private long smokeStartTime;
    private long missileStartTime;
    private long missilesElapsed;
    private MainThread thread;
    private Background bg;
    private Player player;
    private boolean newGameCreated;
    private int best;
    private boolean newBestScore = false;
    private boolean gameFirstStart = true;

    private int maxBorderHeight;
    private int minBorderHeight;
    //increase to lower difficulty
    private int progressDenom = 20;
    private boolean topDown = true;
    private boolean botDown = true;

    private Explosion explosion;
    private long startReset;
    private boolean reset;
    private boolean dissapear;
    private boolean started;

    private ArrayList<Smokepuff> smoke;
    private ArrayList<Missile> missiles;
    private ArrayList<TopBorder> topBorder;
    private ArrayList<BotBorder> botBorder;

    private Random rand = new Random();

    public GamePanel(Context context) {

        super(context);
        //add the callback to the surfaceview to intercept events
        getHolder().addCallback(this);

        //make gamePanel focusable so it can handle events
        setFocusable(true);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

        boolean retry = true;
        int counter = 0;
        while (retry && counter < 1000) {
            counter++;
            try {

                SharedPreferences settings = getContext().getSharedPreferences("MyGameData", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt("savedBest", best);
                editor.commit();

                thread.setRunning(false);
                thread.join();
                retry = false;
                thread = null;

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {


        bg = new Background(BitmapFactory.decodeResource(getResources(), R.drawable.grassbg1));
        player = new Player(BitmapFactory.decodeResource(getResources(), R.drawable.helicopter), 65, 25, 3);
        smoke = new ArrayList<Smokepuff>();
        missiles = new ArrayList<Missile>();

        topBorder = new ArrayList<TopBorder>();
        botBorder = new ArrayList<BotBorder>();

        smokeStartTime = System.nanoTime();
        missileStartTime = System.nanoTime();

        //for saving settings after app resart
        SharedPreferences settings = getContext().getSharedPreferences("MyGameData", Context.MODE_PRIVATE);
        int savedBest = settings.getInt("savedBest", best);
        best = savedBest;

        thread = new MainThread(getHolder(), this);
        //we can safely start the game loop
        thread.setRunning(true);

        //if(thread.getState() == Thread.State.NEW){
        thread.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            if (!player.getPlaying() && newGameCreated && reset) {

                player.setPlaying(true);
                player.setUp(true);
            }

            if (player.getPlaying()) {

                if (!started) started = true;
                reset = false;
                player.setUp(true);
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            player.setUp(false);
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void update() {

        if (player.getPlaying()) {

            if (botBorder.isEmpty()) {

                player.setPlaying(false);
                return;
            }

            if (topBorder.isEmpty()) {

                player.setPlaying(false);
                return;
            }

            bg.update();
            player.update();

            //calculate min and max height of borders
            maxBorderHeight = 30 + player.getScore() / progressDenom;
            if (maxBorderHeight > HEIGHT / 4) maxBorderHeight = HEIGHT / 4;
            minBorderHeight = 5 + player.getScore() / progressDenom;

            //check topBoreder collision

            for (int i = 0; i < topBorder.size(); i++) {
                if (collision(topBorder.get(i), player)) {
                    player.setPlaying(false);
                }
            }

            //chceck botBorder colliosion

            for (int i = 0; i < botBorder.size(); i++) {
                if (collision(botBorder.get(i), player)) {
                    player.setPlaying(false);
                }
            }

            //update topBorder
            this.updateTopBorder();

            //update botBorder
            this.updateBotBorder();

            //add missles on timer
            missilesElapsed = (System.nanoTime() - missileStartTime) / 1000000;
            if (missilesElapsed > (2000 - player.getScore() / 2)) {
                //first missile always starts from middle
                if (missiles.size() == 0) {
                    missiles.add(new Missile(BitmapFactory.decodeResource(getResources(), R.drawable.missile), WIDTH + 10, HEIGHT / 2, 45, 15, player.getScore(), 13));
                } else {
                    missiles.add(new Missile(BitmapFactory.decodeResource(getResources(), R.drawable.missile), WIDTH + 10, (int) (rand.nextDouble() * (HEIGHT - (maxBorderHeight * 2)) + maxBorderHeight + 10), 45, 15, player.getScore(), 13));
                }
                missileStartTime = System.nanoTime();
            }
            //loop through all the missiles and check the collision
            for (int i = 0; i < missiles.size(); i++) {
                missiles.get(i).update();
                //if hit remove missile / stop game
                if (collision(missiles.get(i), player)) {
                    missiles.remove(i);
                    //godmode(true) do testoania
                    player.setPlaying(false);
                    break;
                }
                //if way of the screen remove missile
                if (missiles.get(i).getX() < -40) {
                    missiles.remove(i);
                    break;
                }
            }


            //add smoke puffs on timer
            long elapsed = (System.nanoTime() - smokeStartTime) / 1000000;
            if (elapsed > 120) {
                smoke.add(new Smokepuff(player.getX(), player.getY() + 10));
                smokeStartTime = System.nanoTime();
            }

            for (int i = 0; i < smoke.size(); i++) {
                smoke.get(i).update();
                if (smoke.get(i).getX() < -10) {
                    smoke.remove(i);
                }
            }
        } else {
            player.resetDY();
            if (!reset) {

                newGameCreated = false;
                startReset = System.nanoTime();
                reset = true;
                dissapear = true;
                explosion = new Explosion(BitmapFactory.decodeResource(getResources(), R.drawable.explosion), player.getX(), player.getY() - 30, 100, 100, 25);
            }

            explosion.update();

            long resedElapsed = (System.nanoTime() - startReset) / 1000000;

            if (gameFirstStart) {
                newGame();
                gameFirstStart = false;
            } else if (resedElapsed > 2500 && !newGameCreated) {
                newGame();
            }
        }
    }

    public void updateTopBorder() {

        //every 50 points insert randomly placed top blocks that breaks the pattern
        if (player.getScore() % 50 == 0) {
            topBorder.add(new TopBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), topBorder.get(topBorder.size() - 1).getX() + 20, 0, (int) ((rand.nextDouble() * (maxBorderHeight)) + minBorderHeight)));
        }
        for (int i = 0; i < topBorder.size(); i++) {
            topBorder.get(i).update();
            if (topBorder.get(i).getX() < -20) {
                topBorder.remove(i);

                if (topBorder.get(topBorder.size() - 1).getHeight() >= maxBorderHeight) {
                    topDown = false;
                }
                if (topBorder.get(topBorder.size() - 1).getHeight() <= minBorderHeight) {
                    topDown = true;
                }

                if (topDown) {
                    topBorder.add(new TopBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), topBorder.get(topBorder.size() - 1).getX() + 20, 0, topBorder.get(topBorder.size() - 1).getHeight() + 1));
                } else {
                    topBorder.add(new TopBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), topBorder.get(topBorder.size() - 1).getX() + 20, 0, topBorder.get(topBorder.size() - 1).getHeight() - 1));
                }
            }
        }
    }

    public void updateBotBorder() {

        //every 40 points insert randomly placed bot blocks that breaks the patters
        if (player.getScore() % 40 == 0) {
            botBorder.add(new BotBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), botBorder.get(botBorder.size() - 1).getX() + 20, (int) ((rand.nextDouble() * maxBorderHeight) + (HEIGHT - maxBorderHeight) - 20)));
        }

        for (int i = 0; i < botBorder.size(); i++) {
            botBorder.get(i).update();

            if (botBorder.get(i).getX() < -20) {
                botBorder.remove(i);

                if (botBorder.get(botBorder.size() - 1).getY() <= HEIGHT - maxBorderHeight) {
                    botDown = true;
                }
                if (botBorder.get(botBorder.size() - 1).getY() >= (HEIGHT - minBorderHeight) - 10) {
                    botDown = false;
                }

                if (botDown) {
                    botBorder.add(new BotBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), botBorder.get(botBorder.size() - 1).getX() + 20, botBorder.get(botBorder.size() - 1).getY() + 1));
                } else {
                    botBorder.add(new BotBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), botBorder.get(botBorder.size() - 1).getX() + 20, botBorder.get(botBorder.size() - 1).getY() - 1));
                }
            }
        }
    }

    public boolean collision(GameObject a, GameObject b) {

        return Rect.intersects(a.getRectangle(), b.getRectangle());
    }

    @Override
    public void draw(Canvas canvas) {

        super.draw(canvas);
        final float scaleFactorX = getWidth() / (WIDTH * 1.f);
        final float scaleFactorY = getHeight() / (HEIGHT * 1.f);
        if (canvas != null) {
            final int savedState = canvas.save();
            canvas.scale(scaleFactorX, scaleFactorY);
            bg.draw(canvas);
            if (!dissapear) {
                player.draw(canvas);
            }

            for (Smokepuff sp : smoke) {
                sp.draw(canvas);
            }

            //draw topBorder
            for (TopBorder tb : topBorder) {
                tb.draw(canvas);
            }

            //draw botBorder
            for (BotBorder bb : botBorder) {
                bb.draw(canvas);
            }

            for (Missile ms : missiles) {
                ms.draw(canvas);
            }

            if (started) {

                explosion.draw(canvas);
            }
            drawNewBest(canvas);

            drawText(canvas);

            canvas.restoreToCount(savedState);
        }
    }

    public void newGame() {

        dissapear = false;
        botBorder.clear();
        topBorder.clear();
        missiles.clear();
        smoke.clear();
        newBestScore = false;

        minBorderHeight = 5;
        maxBorderHeight = 30;

        player.setY(HEIGHT / 2);
        player.resetDY();

        player.resetScore();

        //create initial borders
        for (int i = 0; i * 20 < getWidth() + 80; i++) {
            if (i == 0) {
                topBorder.add(new TopBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), i * 20, 0, 10));
            } else {
                topBorder.add(new TopBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), i * 20, 0, 10));
            }
        }

        for (int i = 0; i * 20 < getWidth() + 80; i++) {
            if (i == 0) {
                botBorder.add(new BotBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), i * 20, HEIGHT - minBorderHeight));
            } else {
                botBorder.add(new BotBorder(BitmapFactory.decodeResource(getResources(), R.drawable.brick), i * 20, HEIGHT - minBorderHeight));
            }
        }

        newGameCreated = true;
    }

    public void drawText(Canvas canvas) {

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(30);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("DISTANCE " + (player.getScore() * 3), 10, HEIGHT - 10, paint);
        canvas.drawText("BEST " + (best * 3), WIDTH - 215, HEIGHT - 10, paint);

        if (!player.getPlaying() && newGameCreated && reset) {

            Paint paint1 = new Paint();
            paint1.setTextSize(40);
            paint1.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("PRES TO START", WIDTH / 2 - 50, HEIGHT / 2, paint1);

            paint1.setTextSize(20);
            canvas.drawText("PRESS AND HOLD TO GO UP", WIDTH / 2 - 50, HEIGHT / 2 + 20, paint1);
            canvas.drawText("RELEASE TO GO DOWN", WIDTH / 2 - 50, HEIGHT / 2 + 40, paint1);
        }
    }

    public void drawNewBest(Canvas canvas) {

        if (player.getScore() > best) {

            best = player.getScore();
            newBestScore = true;
        }

        if (newBestScore && !player.getPlaying()) {
            Paint paint = new Paint();
            paint.setTextSize(40);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("NEW BEST SCORE: " + (best * 3), WIDTH / 4, HEIGHT / 2, paint);
        }
    }
}
