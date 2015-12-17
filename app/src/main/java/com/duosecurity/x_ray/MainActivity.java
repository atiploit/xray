package com.duosecurity.x_ray;

import com.duosecurity.x_ray.device.vulnerability.test.adapter.RecyclerAdapter;

import fuzion24.device.vulnerability.test.ResultsCallback;
import fuzion24.device.vulnerability.test.VulnerabilityTestResult;
import fuzion24.device.vulnerability.test.VulnerabilityTestRunner;
import fuzion24.device.vulnerability.util.DeviceInfo;
import com.duosecurity.x_ray.device.vulnerability.vulnerabilities.VulnerabilityResultSerializer;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ActionBarActivity {

  private static final String SERIALIZABLE_RESULTS = "SERIALIZABLE_RESULTS";

  private static final String TAG = "VULN_TEST";
  private static final String DEBUG="DEBUG";

  private DeviceInfo devInfo;
  private ArrayList<VulnerabilityTestResult> testResults;
  private RecyclerView recyclerView;
  private RecyclerAdapter recyclerAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (savedInstanceState != null && savedInstanceState.containsKey(SERIALIZABLE_RESULTS)) {
      testResults = (ArrayList<VulnerabilityTestResult>) savedInstanceState.getSerializable(SERIALIZABLE_RESULTS);
    } else {
      testResults = new ArrayList<>();
    }

    recyclerView = (RecyclerView) findViewById(R.id.recyclerView);
    recyclerAdapter = new RecyclerAdapter(MainActivity.this, testResults);

    recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
    recyclerView.setAdapter(recyclerAdapter);

    devInfo = DeviceInfo.getDeviceInfo();

    StringBuilder sb = new StringBuilder();
    for (String s : devInfo.getSupportedABIS()) {
      sb.append(s);
      sb.append(" ");
    }

    Button fabStart = (Button) findViewById(R.id.fabStart);

    fabStart.setOnClickListener(new View.OnClickListener(){
      @Override
      public void onClick(View v){
        Button fabStart = (Button) findViewById(R.id.fabStart);
        fabStart.setVisibility(View.GONE);
        runTestSuite();
        new HttpAsyncTask().execute("http://duo-xray-server.appspot.com/Wut");
      }
    });
  }

  private void runTestSuite() {
    new VulnerabilityTestRunner(MainActivity.this, true, new ResultsCallback() {
      @Override
      public void finished(final List<VulnerabilityTestResult> results) {
        Log.d(TAG, "Device Vulnerability callback, finished");

        testResults.clear();
        testResults.addAll(results);

        recyclerAdapter.updateResults(results);

        showScanResume(results);
      }
    }).execute();
  }

  private void showScanResume(final List<VulnerabilityTestResult> results) {
    int numberOfFailed = 0;

    for (VulnerabilityTestResult result : results) {
      if (result.getException() != null) {
        continue;
      }

      if (result.isVulnerable()) {
        numberOfFailed++;
      }
    }

    MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(this)
            .title(R.string.scan_details)
            .customView(R.layout.dialog_scan_details_layout, true)
            .positiveText(R.string.dismiss);

    if (numberOfFailed > 0) {
      dialogBuilder.negativeText(R.string.my_device_is_vulnerable_info)
              .onNegative(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(MaterialDialog materialDialog, DialogAction dialogAction) {
                  Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nowsecure.com/blog/2015/11/18/my-device-is-vulnerable-now-what/"));
                  startActivity(browserIntent);
                }
              });
    }
  }

  private class HttpAsyncTask extends AsyncTask<String, Void, String> {
    @Override
    protected String doInBackground(String... urls) {
      return POST(urls[0]);
    }

  }

  public String POST(String url){
    InputStream inputStream = null;
    String result = "";
    try {
      // 1. create HttpClient
      HttpClient httpclient = new DefaultHttpClient();

      // 2. make POST request to the given URL
      HttpPost httpPost = new HttpPost(url);

      String json = "";

      // 3. build jsonObject

      JSONObject jsonObject = VulnerabilityResultSerializer.serializeResultsToJson(testResults, devInfo);

      // 4. convert JSONObject to JSON to String
      json = jsonObject.toString(4);
      Log.d(DEBUG, json);
      // 5. set json to StringEntity
      StringEntity se = new StringEntity(json);

      // 6. set httpPost Entity
      httpPost.setEntity(se);

      // 7. Set some headers to inform server about the type of the content
      httpPost.setHeader("Accept", "application/json");
      httpPost.setHeader("Content-type", "application/json");

      // 8. Execute POST request to the given URL
      HttpResponse httpResponse = httpclient.execute(httpPost);

      // 9. receive response as inputStream
      inputStream = httpResponse.getEntity().getContent();

      // 10. convert inputstream to string
      if(inputStream != null)
        result = convertInputStreamToString(inputStream);
      else
        result = "Did not work!";
      //Log.d(DEBUG, result);
    } catch (Exception e) {
      Log.d("InputStream", e.getLocalizedMessage());
    }

    // 11. return result
    return result;
  }

  private static String convertInputStreamToString(InputStream inputStream) throws IOException {
    BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
    String line = "";
    String result = "";
    while((line = bufferedReader.readLine()) != null)
      result += line;

    inputStream.close();
    return result;

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }
}
