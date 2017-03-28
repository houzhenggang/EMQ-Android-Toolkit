package io.emqtt.emqandroidtoolkit.ui.activity;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.content.Context;
import android.content.Intent;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import io.emqtt.emqandroidtoolkit.Constant;
import io.emqtt.emqandroidtoolkit.R;
import io.emqtt.emqandroidtoolkit.event.MQTTActionEvent;
import io.emqtt.emqandroidtoolkit.event.MessageEvent;
import io.emqtt.emqandroidtoolkit.model.Connection;
import io.emqtt.emqandroidtoolkit.model.EmqMessage;
import io.emqtt.emqandroidtoolkit.model.Publication;
import io.emqtt.emqandroidtoolkit.model.Subscription;
import io.emqtt.emqandroidtoolkit.ui.adapter.ConnectionViewPagerAdapter;
import io.emqtt.emqandroidtoolkit.ui.base.ToolBarActivity;
import io.emqtt.emqandroidtoolkit.ui.fragment.PublicationListFragment;
import io.emqtt.emqandroidtoolkit.ui.fragment.SubscriptionListFragment;
import io.emqtt.emqandroidtoolkit.util.LogUtil;
import io.emqtt.emqandroidtoolkit.util.TipUtil;

public class DashboardActivity extends ToolBarActivity implements SubscriptionListFragment.OnListFragmentInteractionListener, MqttCallback {

    private static final int SUBSCRIPTION = 63;

    private static final int PUBLICATION = 64;

    @BindView(R.id.coordinator_layout) CoordinatorLayout mCoordinatorLayout;
    @BindView(R.id.tab_layout) TabLayout mTabLayout;
    @BindView(R.id.viewpager) ViewPager mViewpager;
    @BindView(R.id.fab) FloatingActionButton mFab;

    private Connection mConnection;
    private Subscription mSubscription;
    private Publication mPublication;

    private MqttAsyncClient mClient;
    private ConnectionViewPagerAdapter mAdapter;

    private List<Subscription> mSubscriptionList;

    private int mCurrentMode = SUBSCRIPTION;

    public static void openActivity(Context context, Connection connection) {
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.putExtra(Constant.ExtraConstant.EXTRA_CONNECTION, connection);
        context.startActivity(intent);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_dashboard;
    }

    @Override
    protected void setUpView() {
        mConnection = getIntent().getParcelableExtra(Constant.ExtraConstant.EXTRA_CONNECTION);
        setTitle(mConnection.getServerURI());
        setSubtitle(getString(R.string.connecting) );

    }

    @Override
    protected void setUpData() {

        EventBus.getDefault().register(this);

        initClient();

        SubscriptionListFragment subscriptionListFragment = SubscriptionListFragment.newInstance();
        PublicationListFragment publicationListFragment = PublicationListFragment.newInstance();

        mAdapter = new ConnectionViewPagerAdapter(getSupportFragmentManager());
        mAdapter.addFragment(subscriptionListFragment, SubscriptionListFragment.TAG);
        mAdapter.addFragment(publicationListFragment, PublicationListFragment.TAG);

        mViewpager.setAdapter(mAdapter);
        mTabLayout.setupWithViewPager(mViewpager);

        mViewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (mAdapter.getItem(position) instanceof SubscriptionListFragment) {
                    mCurrentMode = SUBSCRIPTION;
                } else if (mAdapter.getItem(position) instanceof PublicationListFragment) {
                    mCurrentMode = PUBLICATION;
                }


            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        mSubscriptionList = new ArrayList<>();
        // TODO: 2017/3/28 Test data
        Subscription subscription = new Subscription("test", 1);
        mSubscriptionList.add(subscription);

        connect();

    }


    @Override
    protected void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onListFragmentInteraction(Subscription item) {
        MessageListActivity.openActivity(this,item);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!EventBus.getDefault().isRegistered(this)){
            EventBus.getDefault().register(this);
        }

        if (resultCode == RESULT_OK) {
            if (requestCode == SUBSCRIPTION) {
                Subscription subscription = data.getParcelableExtra(Constant.ExtraConstant.EXTRA_SUBSCRIPTION);
                mSubscription = subscription;
                subscribe(subscription);

            }
            if (requestCode == PUBLICATION) {
                Publication publication = data.getParcelableExtra(Constant.ExtraConstant.EXTRA_PUBLICATION);
                mPublication = publication;
                publish(publication);

            }


        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_connection_detail,menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isConnected()){
            menu.findItem(R.id.action_disconnect).setVisible(true);
            menu.findItem(R.id.action_connect).setVisible(false);
        }else {
            menu.findItem(R.id.action_disconnect).setVisible(false);
            menu.findItem(R.id.action_connect).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_connect) {
            connect();
            return true;
        }
        if (id == R.id.action_disconnect) {
            disconnect();
        }
        return super.onOptionsItemSelected(item);
    }


    @OnClick(R.id.fab)
    public void onClick() {
        if (!isConnected()){
            TipUtil.showSnackbar(mCoordinatorLayout, getString(R.string.hint_disconnect), getString(R.string.connect), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    connect();
                }
            });
            return;
        }

        if (mCurrentMode == SUBSCRIPTION) {
            startActivityForResult(SubscriptionActivity.class, SUBSCRIPTION);
//            SubscriptionFragment subscriptionFragment=new SubscriptionFragment();
//            subscriptionFragment.show(getFragmentManager(),"SubscriptionFragment");
        }else if (mCurrentMode == PUBLICATION){
            startActivityForResult(PublicationActivity.class, PUBLICATION);
        }


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MQTTActionEvent event){

        switch (event.getStatus()) {
            case Constant.MQTTStatusConstant.CONNECT_SUCCESS:
                setSubtitle(getString(R.string.connected));
                invalidateOptionsMenu();

                for (Subscription subscription : mSubscriptionList) {
                    subscribe(subscription);
                }
                break;

            case Constant.MQTTStatusConstant.CONNECT_FAIL:
                setSubtitle(getString(R.string.connect_fail));
                break;

            case Constant.MQTTStatusConstant.SUBSCRIBE_SUCCESS:
                SubscriptionListFragment subscriptionListFragment = (SubscriptionListFragment) mAdapter.getItem(0);
                subscriptionListFragment.addData(mSubscription);

                break;

            case Constant.MQTTStatusConstant.SUBSCRIBE_FAIL:
                break;

            case Constant.MQTTStatusConstant.PUBLISH_SUCCESS:
                PublicationListFragment fragment = (PublicationListFragment) mAdapter.getItem(1);
                fragment.insertData(mPublication);
                break;

            case Constant.MQTTStatusConstant.PUBLISH_FAIL:
                TipUtil.showSnackbar(mCoordinatorLayout,"Publish fail");
                break;

            case Constant.MQTTStatusConstant.CONNECTION_LOST:
                setSubtitle(getString(R.string.disconnect));
                invalidateOptionsMenu();
                break;
            default:
                break;

        }


    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MessageEvent event){
        SubscriptionListFragment subscriptionListFragment = (SubscriptionListFragment) mAdapter.getItem(0);
        subscriptionListFragment.updateData(event.getMessage());
    }


    @Override
    public void connectionLost(Throwable cause) {
        LogUtil.e("connectionLost");
        EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.CONNECTION_LOST,null,cause));

    }


    @Override
    public void messageArrived(final String topic, final MqttMessage message) throws Exception {
        LogUtil.d("topic is " + topic + "\tmessage is " + message.toString());
        EventBus.getDefault().postSticky(new MessageEvent(new EmqMessage(topic, message)));


    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        LogUtil.d("deliveryComplete");

    }

    @Override
    protected void onResume() {
        super.onResume();
        MessageEvent event = EventBus.getDefault().getStickyEvent(MessageEvent.class);
        if (event != null) {
            SubscriptionListFragment subscriptionListFragment = (SubscriptionListFragment) mAdapter.getItem(0);
            subscriptionListFragment.updateData(event.getMessage());
        }


    }

    private void initClient(){
        MqttClientPersistence mqttClientPersistence = new MemoryPersistence();
        try {
            mClient = new MqttAsyncClient(mConnection.getServerURI(), mConnection.getClintId(), mqttClientPersistence);
            mClient.setCallback(this);
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }


    private void connect() {
        try {
            setSubtitle(getString(R.string.connecting));
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(mConnection.isCleanSession());
            mClient.connect(options, "Connect", new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.CONNECT_SUCCESS,asyncActionToken));

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.CONNECT_FAIL,asyncActionToken,exception));

                }
            });

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void subscribe(Subscription subscription){
        mSubscription = subscription;
        try {
            mClient.subscribe(subscription.getTopic(), subscription.getQoS(), "Subscribe", new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.SUBSCRIBE_SUCCESS,asyncActionToken));

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.SUBSCRIBE_FAIL,asyncActionToken,exception));


                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publish(Publication publication){

        try {
            mClient.publish(publication.getTopic(), publication.getMessage(), "Publish", new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    LogUtil.d("onSuccess");
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.PUBLISH_SUCCESS,asyncActionToken));


                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    LogUtil.d("onFailure");
                    EventBus.getDefault().post(new MQTTActionEvent(Constant.MQTTStatusConstant.PUBLISH_FAIL,asyncActionToken,exception));


                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void disconnect(){
        try {
            mClient.disconnect();
            setSubtitle(getString(R.string.disconnect));
            invalidateOptionsMenu();
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }


    private boolean isConnected() {
        return mClient != null && mClient.isConnected();
    }

    public List<Subscription> getSubscriptionList() {
        return mSubscriptionList;
    }
}