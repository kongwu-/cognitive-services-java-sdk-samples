package com.azure.cognitiveservices.inkrecognitionsample;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.azure.ai.inkrecognizer.*;
import com.azure.ai.inkrecognizer.model.InkDrawing;
import com.azure.ai.inkrecognizer.model.InkRecognitionRoot;
import com.azure.ai.inkrecognizer.model.InkWord;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

public class NoteTaker extends View {

    private final Path path = new Path();
    private final Paint brush = new Paint();
    private InkStrokeImplementor stroke;
    private final InkRecognizerAsyncClient inkRecognizerAsyncClient;
    private CountDownTimer analysisTimer = null;
    private final ArrayList<InkStroke> strokes = new ArrayList<>();
    private static final String TAG = "InkRecognizer";

    public NoteTaker(Context context) throws Exception {
        super(context);
        String appKey = "<SUBSCRIPTION_KEY>";
        String destinationUrl = "https://api.cognitive.microsoft.com/inkrecognizer";
        inkRecognizerAsyncClient = new InkRecognizerClientBuilder()
                .credentials(new InkRecognizerCredentials(appKey))
                .endpoint(destinationUrl)
                // You can also set this to ApplicationKind.WRITING or ApplicationKind.DRAWING
                // if you know the expected type of ink content
                .applicationKind(ApplicationKind.MIXED)
                // You can set the language here if you know the expected language
                .language("en-US")
                .displayMetrics(getResources().getDisplayMetrics())
                .buildAsyncClient();
        brush.setAntiAlias(true);
        brush.setColor(Color.BLACK);
        brush.setStyle(Paint.Style.STROKE);
        brush.setStrokeJoin(Paint.Join.ROUND);
        brush.setStrokeWidth(3.0f);
    }

    private void startTimer() {
        //The next 2 variables are used for the inactivity timer which triggers recognition
        //after a certain period of inactivity.
        int milliSeconds = 2000;//Time to wait
        int countDownInterval = 1000;//interval
        NoteTaker noteTaker = this;
        analysisTimer = new CountDownTimer(milliSeconds, countDownInterval) {
            public void onTick(long millFinish) {
            }

            public void onFinish() {
                if (strokes.size() != 0) {
                    new recognizeInkTask().execute(noteTaker);
                }

            }
        }.start();
    }

    private static class recognizeInkTask extends AsyncTask<NoteTaker, Integer, NoteTaker> {

        StringBuilder recognizedWords = new StringBuilder();

        @Override
        protected NoteTaker doInBackground(NoteTaker... params) {
            NoteTaker noteTaker = null;
            try {
                noteTaker = params[0];
                if (noteTaker.strokes.size() != 0) {
                    Mono<Response<InkRecognitionRoot>> response = noteTaker.inkRecognizerAsyncClient.recognizeInk(noteTaker.strokes);
                    response.subscribe(r -> showResults(r), e -> e.printStackTrace());
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG, "Error while fetching the response", e);
            }
            return noteTaker;
        }

        @Override
        protected void onPostExecute(NoteTaker noteTaker) {
            Toast toast = Toast.makeText(noteTaker.getContext(), recognizedWords.toString(), Toast.LENGTH_LONG);
            toast.show();
        }

        private void showResults(Response<InkRecognitionRoot> response) {

            if (response.status == 200) {

                InkRecognitionRoot root = response.root;

                recognizedWords.append("\r\nRecognized Text:\r\n");
                Iterable<InkWord> words = root.inkWords();
                for (InkWord word : words) {
                    recognizedWords.append(word.recognizedText());
                    recognizedWords.append(" ");
                }

                Iterable<InkDrawing> drawings = root.inkDrawings();
                recognizedWords.append("\r\nRecognized Shapes:\r\n");
                for (InkDrawing drawing : drawings) {
                    recognizedWords.append(drawing.recognizedShape().toString()).append("\r\n");
                }

            }

        }

    }

    private void cancelTimer() {
        if (analysisTimer != null) {
            analysisTimer.cancel();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                path.moveTo(x, y);
                stroke = new InkStrokeImplementor();
                stroke.addPoint(x, y);
                cancelTimer();
                return true;
            case MotionEvent.ACTION_MOVE:
                path.lineTo(x, y);
                stroke.addPoint(x, y);
                break;
            case MotionEvent.ACTION_UP:
                strokes.add(stroke);
                startTimer();
                break;
            default:
                return false;
        }
        postInvalidate();
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawPath(path, brush);
    }
}
