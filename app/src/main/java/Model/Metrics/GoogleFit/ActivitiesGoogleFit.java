package Model.Metrics.GoogleFit;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.request.SessionReadRequest;
import com.google.android.gms.fitness.result.SessionReadResponse;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import Model.Utils.HttpRequests;
import Model.Users.Login;
import Model.Metrics.DataSender;
import Model.Utils.Urls;

public class ActivitiesGoogleFit implements DataSender {

    private List activityArray;
    private JSONObject json;
    private static final String TAG = "ActivitiesGoogleFit";

    private int extractionCounter = 0;

    public ActivitiesGoogleFit() {
        activityArray = new ArrayList();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void extractActivityDataByDate(Context context, long startTime, long endTime) {

        try {

            Log.i(TAG, "extractActivityDataByDate: got startTime = " + Long.toString(startTime) + ", endTime = " + Long.toString(endTime));

            extractionCounter++;

            // Note: The android.permission.ACTIVITY_RECOGNITION permission is
            // required to read DataType.TYPE_ACTIVITY_SEGMENT
            SessionReadRequest request = new SessionReadRequest.Builder()
                    .readSessionsFromAllApps()
                    // Activity segment data is required for details of the fine-
                    // granularity sleep, if it is present.
                    .read(DataType.TYPE_ACTIVITY_SEGMENT)
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                    .build();

            Task<SessionReadResponse> task = Fitness.getSessionsClient(context,
                    GoogleSignIn.getLastSignedInAccount(context))
                    .readSession(request);

            task.addOnSuccessListener(response -> {

                List<Session> activitiesSessions = response.getSessions().stream()
                        .collect(Collectors.toList());

                if (activitiesSessions.size() == 0) {
                    if (extractionCounter < 3) {
                        extractActivityDataByDate(context, startTime, endTime);
                    }
                    return;
                }

                extractionCounter = 0;

                for (Session session : activitiesSessions) {
                    Log.d(TAG, String.format("Activities between %d and %d",
                            session.getStartTime(TimeUnit.MILLISECONDS),
                            session.getEndTime(TimeUnit.MILLISECONDS)));

                    // If the sleep session has finer granularity sub-components, extract them:
                    List<DataSet> dataSets = response.getDataSet(session);
                    for (DataSet dataSet : dataSets) {
                        for (DataPoint point : dataSet.getDataPoints()) {
                            String activity = point.getValue(Field.FIELD_ACTIVITY).asActivity();
                            long start = point.getStartTime(TimeUnit.MILLISECONDS);
                            long end = point.getEndTime(TimeUnit.MILLISECONDS);
                            Log.d(TAG, String.format("\t* %s between %d and %d", activity, start, end));

                            //ignore sleeping data
                            if (activity.equals("sleep.deep") || activity.equals("sleep.light"))
                                continue;

                            JSONObject json = new JSONObject();
                            try {
                                json.put("StartTime", start);
                                json.put("EndTime", end);
                                json.put("State", activity);
                                if (!activityArray.contains(json))
                                    activityArray.add(json);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                makeBodyJson(startTime);
                sendDataToServer(HttpRequests.getInstance(context));

            })
                    .addOnFailureListener(response -> {
                        Log.e(TAG, "extractActivityDataByDates: failed to extract activity data");
                        if (extractionCounter < 3) {
                            Log.i(TAG, "extractActivityDataByDates: retry extract activity data. counter value = " + Integer.toString(extractionCounter));
                            extractActivityDataByDate(context, startTime, endTime);
                        } else {
                            extractionCounter = 0;
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG,"Error occured: ");
            e.printStackTrace();

        }

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void extractActivityData(Context context) {

        extractionCounter++;

        Calendar midnight = Calendar.getInstance();

        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);

        long startTime = midnight.getTimeInMillis();

        // Note: The android.permission.ACTIVITY_RECOGNITION permission is
        // required to read DataType.TYPE_ACTIVITY_SEGMENT
        SessionReadRequest request = new SessionReadRequest.Builder()
                .readSessionsFromAllApps()
                // Activity segment data is required for details of the fine-
                // granularity sleep, if it is present.
                .read(DataType.TYPE_ACTIVITY_SEGMENT)
                .setTimeInterval(startTime, System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                .build();

        Task<SessionReadResponse> task = Fitness.getSessionsClient(context,
                GoogleSignIn.getLastSignedInAccount(context))
                .readSession(request);

        task.addOnSuccessListener(response -> {

            List<Session> activitiesSessions = response.getSessions().stream()
                    .collect(Collectors.toList());

            if (activitiesSessions.size() == 0){
                if (extractionCounter < 3) {
                    extractActivityData(context);
                }
                return;
            }

            extractionCounter = 0;

            for (Session session : activitiesSessions) {

                // If the sleep session has finer granularity sub-components, extract them:
                List<DataSet> dataSets = response.getDataSet(session);
                for (DataSet dataSet : dataSets) {
                    for (DataPoint point : dataSet.getDataPoints()) {
                        String activity = point.getValue(Field.FIELD_ACTIVITY).asActivity();
                        long start = point.getStartTime(TimeUnit.MILLISECONDS);
                        long end = point.getEndTime(TimeUnit.MILLISECONDS);

                        //ignore sleeping data
                        if (activity.equals("sleep.deep") || activity.equals("sleep.light"))
                            continue;

                        Log.d(TAG, String.format("\t* %s between %d and %d", activity, start, end));

                        JSONObject json = new JSONObject();
                        try {
                            json.put("StartTime", start);
                            json.put("EndTime", end);
                            json.put("State", activity);

                            if (!activityArray.contains(json))
                                activityArray.add(json);

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }
            makeBodyJson(System.currentTimeMillis());
            sendDataToServer(HttpRequests.getInstance(context));
        })
                .addOnFailureListener(response -> {
                    Log.e(TAG, "extractActivityData: failed to extract activity data");
                    if (extractionCounter < 3){
                        Log.i(TAG, "extractActivityData: retry extract activity data. counter value = " + extractionCounter);
                        extractActivityData(context);
                    }
                    else{
                        extractionCounter = 0;
                    }
                });
    }

    public void makeBodyJson(long time) {
        JSONObject json = new JSONObject();
        JSONArray array = new JSONArray(activityArray);
        try {
            json.put("ValidTime", time);
            json.put("Data", array);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.json = json;
    }

    public JSONObject getJson() {
        return this.json;
    }

    public void clearJson() {
        this.activityArray = new ArrayList();
        this.json = null;
    }

    public void sendDataToServer(HttpRequests httpRequests) {

        if (activityArray.size() == 0){
            Log.e(TAG, "No data in activity.");
            return;
        }

        try {
            httpRequests.sendPostRequest(getJson(), Urls.urlPostActivity, Login.getToken(HttpRequests.getContext()));
            clearJson();
        } catch (Exception e) {
            Log.e(TAG, "No data in activity.");
            e.printStackTrace();
        }
    }
}
