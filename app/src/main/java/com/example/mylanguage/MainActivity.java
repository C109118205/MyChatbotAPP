package com.example.mylanguage;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeMetadata;
import com.google.cloud.speech.v1p1beta1.LongRunningRecognizeResponse;
import com.google.cloud.speech.v1p1beta1.SpeechSettings;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;

import android.media.MediaPlayer;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.google.cloud.speech.v1p1beta1.RecognitionAudio;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.RecognizeResponse;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionResult;

public class MainActivity extends AppCompatActivity {
    com.google.cloud.texttospeech.v1.AudioEncoding ttsAudioEncoding = com.google.cloud.texttospeech.v1.AudioEncoding.LINEAR16;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 2;
    private static final int REQUEST_READ_EXTERNAL_STORAGE_PERMISSION = 3;
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE_PERMISSION = 4;
    private boolean isRecording = false;
    private MediaPlayer mediaPlayer;
    private String outputspeech;
    private MediaRecorder mediaRecorder;


    private Button ttsButton;
    private EditText message_text;
    private RecyclerView recyclerView;
    List<Message> messageList;
    MessageAdapter messageAdapter;
    Button mic_button;
    RadioButton ChineseButton;
    RadioButton EnglishButton;
    String LanguageCode = "en-US";
    RadioGroup radioGroup;
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        messageList = new ArrayList<>();
        ttsButton = findViewById(R.id.send_message_button);
        message_text = findViewById(R.id.message_text);
        recyclerView = findViewById(R.id.message_list);
        mic_button = findViewById(R.id.mic_button);
        radioGroup = findViewById(R.id.radioGroup);
        ChineseButton = findViewById(R.id.ChineseButton);
        EnglishButton = findViewById(R.id.EnglishButton);
        EnglishButton.setChecked(true);
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.ChineseButton){
                    LanguageCode = "zh-TW";
                }
                else if (checkedId == R.id.EnglishButton){
                    LanguageCode = "en-US";
                }
            }
        });

        ttsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = message_text.getText().toString();
                addToChat(text,Message.SENT_BY_ME);
                callAPI(text);
                message_text.setText("");
            }
        });
        mic_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.out.println("按鈕測試");
                if(checkAudioPermissions()==true && checkReadStoragePermissions()==true){
                    if (!isRecording) {
                        startRecording();
                    } else {
                        stopRecording();
                    }
                }
//                STT();
            }
        });


    }
    void addToChat(String message,String sentBy){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageList.add(new Message(message,sentBy));
                messageAdapter.notifyDataSetChanged();
                recyclerView.smoothScrollToPosition(messageAdapter.getItemCount());
            }
        });

    }

    void addResponse(String response){
        messageList.remove(messageList.size()-1);
        addToChat(response,Message.SENT_BY_BOT);
        TTS(response);
        String filePath = getExternalFilesDir(null) + "/output.mp3";
        playAudio(filePath);
    }

    void callAPI(String question){
        //okhttp
        messageList.add(new Message("Typing...",Message.SENT_BY_BOT));

        JSONObject  josnBody = new JSONObject();

        try {
            josnBody.put("model","text-davinci-003");
            josnBody.put("prompt",question);
            josnBody.put("max_tokens",4000);
            josnBody.put("temperature",0);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        RequestBody body = RequestBody.create(josnBody.toString(),JSON);
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/completions")
                .header("Authorization","Bearer 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")
                .post(body)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                addResponse("Failed to load response due to "+e.getMessage());
            }
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(response.isSuccessful()){
                    JSONObject jsonObject = null;
                    try {
                        jsonObject = new JSONObject(response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray("choices");
                        String result = jsonArray.getJSONObject(0).getString("text");
                        addResponse(result.trim());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }else{
                    addResponse("Failed to load response due to "+response.body().toString());
                }
            }
        });
    }

    //TTS
    private void TTS(String message) {
        VoiceSelectionParams voiceSelectionParams = null;
        try {
            // 使用你的API金鑰進行身份驗證
            GoogleCredentials credentials = GoogleCredentials.fromStream(getResources().openRawResource(R.raw.key));
            TextToSpeechSettings settings = TextToSpeechSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();

            // 建立TextToSpeechClient
            TextToSpeechClient textToSpeechClient = TextToSpeechClient.create(settings);

            // 設定輸入文本
            SynthesisInput input = SynthesisInput.newBuilder().setText(message).build();

            // 設定音訊配置
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(ttsAudioEncoding.MP3)
                    .build();

            // 設定語音選擇參數

            voiceSelectionParams = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(LanguageCode)
                    .setSsmlGender(SsmlVoiceGender.FEMALE)
                    .build();

            // 透過TTS API進行文字轉語音
            ByteString audioContents = textToSpeechClient.synthesizeSpeech(input, voiceSelectionParams, audioConfig).getAudioContent();
            String filePath = getExternalFilesDir(null) + "/output.mp3";
            try (OutputStream out = new FileOutputStream(filePath)) {
                out.write(audioContents.toByteArray());
                System.out.println("Audio content written to file \"output.mp3\"");
            }

            // 關閉TextToSpeechClient
            textToSpeechClient.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //STT
    private void STT(){
        SpeechClient SpeechToTextClient = null;

        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(getResources().openRawResource(R.raw.key));
            SpeechSettings settings = SpeechSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(credentials)).build();
            // 建立TextToSpeechClient
            SpeechToTextClient = SpeechClient.create(settings);
            Path path = Paths.get("/storage/emulated/0/Android/data/com.example.mylanguage/cache/speech.mp3");
            byte[] data = Files.readAllBytes(path);
            ByteString audioBytes = ByteString.copyFrom(data);

            // The path to the audio file to transcribe
            RecognitionAudio audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build();

            // Builds the sync recognize request
            RecognitionConfig config = RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.MP3)
                            .setSampleRateHertz(8000)
                            .setLanguageCode(LanguageCode)
                            .build();

            OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata> response =
                    SpeechToTextClient.longRunningRecognizeAsync(config, audio);
            while (!response.isDone()) {
                System.out.println("Waiting for response...");
                Thread.sleep(10000);
            }

            List<SpeechRecognitionResult> results = response.get().getResultsList();
            if (results.isEmpty()) {
                System.out.println("STT 無法辨識語音");
            } else {
                SpeechRecognitionResult result = results.get(0);
                SpeechRecognitionAlternative alternative = result.getAlternatives(0);
                String transcript = alternative.getTranscript();
                System.out.println("STT 成功，辨識結果為：" + transcript);
                message_text.setText(transcript);
            }
        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            // 確保在使用後關閉 SpeechClient
            if (SpeechToTextClient != null) {
                SpeechToTextClient.close();
                System.out.printf("speech is close");

            }
        }
    }


    private boolean checkAudioPermissions() {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"audio權限沒過",Toast.LENGTH_LONG).show();
                requestAudioPermissions();
                return false;
            }

        Toast.makeText(this,"audio權限過",Toast.LENGTH_LONG).show();
        return true;
    }
    private boolean checkWriteStoragePermissions() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,"WriteStorage權限沒過",Toast.LENGTH_LONG).show();
                requestWriteStoragePermissions();
                return false;
        }
        Toast.makeText(this,"WriteStorage權限過",Toast.LENGTH_LONG).show();
        return true;
    }

    private boolean checkReadStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"ReadStorage權限沒過",Toast.LENGTH_LONG).show();
            requestReadStoragePermissions();
            return false;
        }
        Toast.makeText(this,"ReadStorage權限過",Toast.LENGTH_LONG).show();
        return true;
    }
    private boolean checkMANAGEStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,"MWriteStorage權限沒過",Toast.LENGTH_LONG).show();
            requestMANAGESStoragePermissions();
            return false;
        }
        Toast.makeText(this,"MWriteStorage權限過",Toast.LENGTH_LONG).show();
        return true;
    }
    private void requestAudioPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        Toast.makeText(this,"audio權限要求成功",Toast.LENGTH_LONG).show();
    }
    private void requestWriteStoragePermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION);
        Toast.makeText(this,"wStorage權限要求成功",Toast.LENGTH_LONG).show();
    }
    private void requestReadStoragePermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE_PERMISSION);
        Toast.makeText(this,"rStorage權限要求成功",Toast.LENGTH_LONG).show();
    }
    private void requestMANAGESStoragePermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, REQUEST_MANAGE_EXTERNAL_STORAGE_PERMISSION);
        Toast.makeText(this,"MStorage權限要求成功",Toast.LENGTH_LONG).show();
    }
    private void startRecording() {
        try {
            // 建立 MediaRecorder
            outputspeech = getExternalCacheDir().getAbsolutePath() + "/speech.mp3";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(outputspeech);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            Toast.makeText(this,"開始錄音",Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void stopRecording() {

        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            isRecording = false;
            Toast.makeText(this,"停止錄音",Toast.LENGTH_LONG).show();
            // 開始語音轉文字
            System.out.println(outputspeech);

            STT();

        }
    }
    private void playAudio(String filePath) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, Uri.parse(filePath));
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 播放完畢後的處理
                    releaseMediaPlayer();
                }
            });
        }

        mediaPlayer.start();
    }
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
    }
    }

