package Model.Metrics.GoogleFit;


import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.HistoryClient;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import Model.Utils.HttpRequests;
import Model.Users.Login;
import Model.Metrics.DataSender;
import Model.Utils.Urls;

import static com.google.android.gms.fitness.data.Field.FIELD_STEPS;

public class StepsGoogleFit implements DataSender {

    private static final String TAG = "StepsGoogleFit";
    private int steps = 0;
    private boolean calculated = false;
    private int extractionCounter = 0;
    private JSONObject toSend;

    public StepsGoogleFit() {
    }

    public void getDataFromPrevDay(Context context, GoogleSignInOptionsExtension fitnessOptions) {

        extractionCounter ++;

        GoogleSignInAccount googleSignInAccount =
                GoogleSignIn.getAccountForExtension(context, fitnessOptions);

        /**
         * steps
         */
        Calendar midnight = Calendar.getInstance();

        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);

        long endTime = System.currentTimeMillis();
        long startTime = midnight.getTimeInMillis();

        DataReadRequest request = new DataReadRequest.Builder()
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .build();

        HistoryClient historyClient = Fitness.getHistoryClient(context, googleSignInAccount);
        Task<DataReadResponse> task = historyClient.readData(request); //computed from midnight of the current day on the device's current timezone

        task.addOnSuccessListener(response -> {

            extractionCounter = 0;

            DataSet dataset = response.getDataSets().get(0);

            for (DataPoint datapoint :
                    dataset.getDataPoints()) {
                steps += datapoint.getValue(FIELD_STEPS).asInt();
            }
            makeBodyJson(endTime);
            Log.i("Total steps of the day:", "************ " + Integer.toString(steps) + " *************");
            sendDataToServer(HttpRequests.getInstance(context));

            calculated = true;

        })
                .addOnFailureListener(response -> {

                    Log.e(TAG, "getDataFromPrevDay: failed to extract steps data");
                    if (extractionCounter < 3){
                        Log.i(TAG, "getDataFromPrevDay: retry extract steps data. counter value = " + extractionCounter);
                        getDataFromPrevDay(context, fitnessOptions);
                    }
                    else{
                        calculated = true;
                        extractionCounter = 0;
                    }
                });

    }

    public void getDataByDate(Context context, GoogleSignInOptionsExtension fitnessOptions, long startTime, long endTime) {

        Log.i(TAG, "getDataByDate: gor startTime = " + Long.toString(startTime )+ ", endTime = " + Long.toString(endTime));

        extractionCounter ++;

        GoogleSignInAccount googleSignInAccount =
                GoogleSignIn.getAccountForExtension(context, fitnessOptions);

        /**
         * steps
         */

        DataReadRequest request = new DataReadRequest.Builder()
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .read(DataType.TYPE_STEP_COUNT_DELTA)
                .build();

        HistoryClient historyClient = Fitness.getHistoryClient(context, googleSignInAccount);
        Task<DataReadResponse> task = historyClient.readData(request); //computed from midnight of the current day on the device's current timezone

        task.addOnSuccessListener(response -> {

            extractionCounter = 0;

            DataSet dataset = response.getDataSets().get(0);

            for (DataPoint datapoint :
                    dataset.getDataPoints()) {
                steps += datapoint.getValue(FIELD_STEPS).asInt();
            }

            Log.i(TAG, "getDataByDate: for date: " + Long.toString(startTime)+ ", amount of steps:" + Integer.toString(steps));

            makeBodyJson(startTime);
            sendDataToServer(HttpRequests.getInstance(context));
            hadBeenCalc();

        })
                .addOnFailureListener(response -> {

                    Log.e(TAG, "getDataByDate: failed to extract steps data");
                    if (extractionCounter < 3){
                        Log.i(TAG, "getDataByDate: retry extract steps data. counter value = " + extractionCounter);
                        getDataByDate(context, fitnessOptions, startTime, endTime);
                    }
                    else{
                        extractionCounter = 0;
                    }
                });

    }

    public JSONObject makeBodyJson(long time) {
        toSend = new JSONObject();
        try {
            toSend.put("ValidTime", time);
            toSend.put("Data", this.steps);
            this.steps = 0;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return toSend;
    }

    public boolean hadBeenCalc() {
        return calculated;
    }

    public void sendDataToServer(HttpRequests httpRequests) {
        try {
            httpRequests.sendPostRequest(toSend, Urls.urlPostSteps, Login.getToken(HttpRequests.getContext()));
        }
        catch (Exception e){
            Log.e(TAG, "No data in steps.");
            e.printStackTrace();
        }
    }
}
