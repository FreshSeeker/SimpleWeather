package com.seekerzhouk.android.simpleweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.gson.Gson;
import com.seekerzhouk.android.simpleweather.R;
import com.seekerzhouk.android.simpleweather.adapter.RecyclerViewAdapter;
import com.seekerzhouk.android.simpleweather.bean.JsonBean;
import com.seekerzhouk.android.simpleweather.fragment.HintFragment;
import com.seekerzhouk.android.simpleweather.receiver.NetWorkStateReceiver;
import com.seekerzhouk.android.simpleweather.utils.ConfigURL;
import com.seekerzhouk.android.simpleweather.utils.SpUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends BaseActivity implements HintFragment.SetCityCallBack {

    private static final String TAG = "MainActivity";
    private static final boolean DBG = true;

    static String district = null;
    public static final int REQUEST_CODE_TO_SET_CITY = 1;

    private RecyclerView mrecyclerView = null;
    private LinearLayout mainLinearLayout = null;
    private LinearLayoutManager linearLayoutManager = null;
    private RecyclerViewAdapter.OnItemClickListener rvaOnItemClickListener = null;

    private NetWorkStateReceiver netWorkStateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerMyReceiver();

        mrecyclerView = findViewById(R.id.recycler_view);
        mainLinearLayout = findViewById(R.id.main_linearLayout);
        SharedPreferences sharedPreferences = getSharedPreferences("FirstDistrict", Context.MODE_PRIVATE);
        district = sharedPreferences.getString("district", null);

        linearLayoutManager = new LinearLayoutManager(MainActivity.this);

        rvaOnItemClickListener = new RecyclerViewAdapter.OnItemClickListener() {
            //跳转到天气详情界面
            @Override
            public void onClick(int position) {
                Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
                intent.putExtra("position", position);
                startActivity(intent);
            }

            //点击位置图标和位置TextView跳转到设置城市界面
            @Override
            public void setLocationClick() {
                setCity();
            }
        };

        //设置分割线
        mrecyclerView.addItemDecoration(new MyDecoration());
        //布局管理器
        mrecyclerView.setLayoutManager(linearLayoutManager);

        refresh();
        setBackground();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(netWorkStateReceiver);
        super.onDestroy();
    }

    private void registerMyReceiver() {
        if (netWorkStateReceiver == null) {
            netWorkStateReceiver = new NetWorkStateReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(netWorkStateReceiver, intentFilter);
    }

    /**
     * 在不同时间段，设置状态栏和actionbar不同的背景颜色
     */
    private void setBackground() {
        TimePicker timePicker = new TimePicker(this);
        int hour = timePicker.getHour();
        if (hour >= 6 && hour <= 11) {
            mainLinearLayout.setBackground(getDrawable(R.drawable.bg_morning));
        } else if (hour > 11 && hour < 14) {
            mainLinearLayout.setBackground(getDrawable(R.drawable.bg_noon));
        } else if (hour >= 14 && hour <=18) {
            mainLinearLayout.setBackground(getDrawable(R.drawable.bg_afternoon));
        } else {
            mainLinearLayout.setBackground(getDrawable(R.drawable.bg_night));
        }
    }

    private void refresh() {
        if (DBG) {
            Log.d(TAG, "refresh, district = " + district);
        }

        //如果district不为空，主界面设置RecyclerView
        if (district != null) {

            // 提示对话框
            final ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setMessage(getResources().getString(R.string.loading));
            progressDialog.show();

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(ConfigURL.getWeatherURL(district))
                    .build();

            Call call = okHttpClient.newCall(request);

            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {

                    String string = response.body().string();
                    Log.i("---", "onResponse: " + string);

                    //获得JsonBean对象
                    Gson gson = new Gson();
                    final JsonBean jsonBean = gson.fromJson(string, JsonBean.class);
                    //保存jsonBean对象
                    SpUtils.putObject(MainActivity.this, jsonBean);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //设置适配器
                            mrecyclerView.setAdapter(new RecyclerViewAdapter(MainActivity.this, rvaOnItemClickListener));

                            progressDialog.hide();
                            Toast.makeText(MainActivity.this, getString(R.string.succeed_refresh_str), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            //当district为空，主界面显示Fragment
        } else {
            HintFragment hintFragment = new HintFragment();
            getFragmentManager().beginTransaction().add(R.id.main_fragment, hintFragment).commitAllowingStateLoss();
        }
    }

    //分割线MyDecoration
    class MyDecoration extends RecyclerView.ItemDecoration {
        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.set(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.dividerHeight));
        }
    }

    //加载自定义标题栏
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    //响应Action按钮的点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if (district == null) {
                    Toast.makeText(this, R.string.location_not_set, Toast.LENGTH_SHORT).show();
                } else {
                    refresh();
                }
                return true;
            case R.id.action_set_location:
                setCity();
                return true;
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }

    }

    @Override
    public void setCity() {
        Intent intent = new Intent(MainActivity.this, SetCityActivity.class);
        startActivityForResult(intent, REQUEST_CODE_TO_SET_CITY);
    }

    private long mBackPressedTime;

    //返回键的时间响应
    @Override
    public void onBackPressed() {
        long curTime = SystemClock.uptimeMillis();
        if ((curTime - mBackPressedTime) < (3 * 1000)) {
            finish();
        } else {
            mBackPressedTime = curTime;
            Toast.makeText(this, R.string.tip_double_click_exit, Toast.LENGTH_LONG).show();
        }
    }

    //从设置城市界面回来时，得到所选地区
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_CODE_TO_SET_CITY) {
            if (data == null) {
                return;
            }
            district = data.getStringExtra("selectedDistrict");
            SharedPreferences sharedPreferences = getSharedPreferences("FirstDistrict", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("district", district);
            editor.apply();
            refresh();
        }
    }
}
