package com.example.wheresmybus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final java.util.UUID UUID = null;
    GoogleMap mGoogleMap;
    SupportMapFragment mapFrag;
    LocationRequest mLocationRequest;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    FusedLocationProviderClient mFusedLocationClient;
    Context context;
    //LatLng[] pino = null;
    MqttHelper mqtt;
    LatLng next=null;
    LatLng past=null;
    boolean ha_localizacao=false;
    boolean primeiro=true;
    double bearing=0;
    String passado="";

    private static final String[] areas= new String[200];
    private static final String TAG = MainActivity.class.getSimpleName();
    String uuid=java.util.UUID.randomUUID().toString();
    String autocarrox;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button selecionar = findViewById(R.id.selecionar);
        Button autocarro=findViewById(R.id.butao_autocarro);
        mqtt = new MqttHelper(getApplicationContext());
        //past=new LatLng(0,0);
        Objects.requireNonNull(getSupportActionBar()).hide();
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        for(int i=0;i<areas.length;i++){
            areas[i]=String.valueOf(i);
        }
        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        assert mapFrag != null;
        mapFrag.getMapAsync(this);

        selecionar.setOnClickListener(new View.OnClickListener() { // BUTAO PARA SELECIONAR O AUTOCARRO
            public void onClick(View v) {

                LayoutInflater layoutInflater = LayoutInflater.from(MainActivity.this);
                View promptView = layoutInflater.inflate(R.layout.selecionar_autocarro, null);
                final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
                alertDialogBuilder.setView(promptView);

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_dropdown_item_1line, areas);
                AutoCompleteTextView textView = (AutoCompleteTextView)
                        promptView.findViewById(R.id.id_text_select);
                textView.setAdapter(adapter);
                alertDialogBuilder.setCancelable(true)
                        .setPositiveButton("OK", (dialog, id) -> {
                            autocarrox=textView.getText().toString();
                            //Toast.makeText(MainActivity.this,uuid , Toast.LENGTH_SHORT).show();
                            String j=autocarrox+"/"+uuid;
                            mqtt.publish("home/wheresmybus/app",j,0);
                            ha_localizacao=false;
                            primeiro=true;

                        });
                AlertDialog b = alertDialogBuilder.create();
                b.show();
                b.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.black));
            }
        });

        autocarro.setOnClickListener(new View.OnClickListener() { // BUTAO PARA FOCAR NO AUTOCARRO
            public void onClick(View v) {
                if(ha_localizacao) {
                    focar(next,17);
                }
                else{
                    Toast.makeText(MainActivity.this,"Não há localizações   : (" , Toast.LENGTH_SHORT).show();

                }
            }
        });


    }

    @Override
    public void onPause() {
        super.onPause();

        //stop location updates when Activity is no longer active
        if (mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;
        mGoogleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(120000); // two minute interval
        mLocationRequest.setFastestInterval(120000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            //Location Permission already granted
            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
            mGoogleMap.setMyLocationEnabled(true);
        } else {
            //Request Location Permission
            checkLocationPermission();
        }

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                //if (autocarrox != null) {
                    mqtt.subscribeToTopic(uuid, 0);

                    mqtt.publish("home/wheresmybus/app",autocarrox+"/"+uuid,0);
                    mqtt.mqttAndroidClient.setCallback(new MqttCallbackExtended() {
                        @Override
                        public void connectComplete(boolean b, String s) { Log.w("connectado!!!!  ", s); }
                        @Override
                        public void connectionLost(Throwable throwable) { }
                        @Override
                        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                            String x = mqttMessage.toString();

                            String[] parts = x.split(",");
                            double part1 = Double.parseDouble(parts[0]); // 004
                            double part2 = Double.parseDouble(parts[1]);
                            String hora=parts[2];

                            next = new LatLng(part1, part2);
                            ha_localizacao = true;


                            if (primeiro) {
                                past = next;
                                primeiro = false;
                            }
                            if(!passado.equals(x)) {
                                googleMap.clear();
                                double fLat = (Math.PI * past.latitude) / 180.0f;
                                double fLng = (Math.PI * past.longitude) / 180.0f;
                                double tLat = (Math.PI * next.latitude) / 180.0f;
                                double tLng = (Math.PI * next.longitude) / 180.0f;

                                double degree = radiansToDegrees(Math.atan2(Math.sin(tLng - fLng) * Math.cos(tLat), Math.cos(fLat) * Math.sin(tLat) - Math.sin(fLat) * Math.cos(tLat) * Math.cos(tLng - fLng)));

                                if (degree >= 0) {
                                    bearing = degree;
                                } else {
                                    bearing = 360 + degree;
                                }


                                    Objects.requireNonNull(googleMap.addMarker(
                                            new MarkerOptions()
                                                    .draggable(false)
                                                    .position(next)
                                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.autocarro))
                                                    .rotation((float) bearing)
                                                    .title(hora))).showInfoWindow();
                            }
                            past = next;
                            passado=x;
                            final Handler handler1 = new Handler();
                            handler1.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    mqtt.publish("home/wheresmybus/app",autocarrox+"/"+uuid,0);
                                }

                        } , 1500);
                        }
                        @Override
                        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {}
                    });
               // }
           }
        }, 2000);

    }

    private void focar(LatLng marker,int zoom) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(marker)      // Sets the center of the map to Mountain View
                .zoom(zoom)               // Sets the zoom

                .build();                   // Creates a CameraPosition from the builder
        mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }
    LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            List<Location> locationList = locationResult.getLocations();
            if (locationList.size() > 0) {
                //The last location in the list is the newest
                Location location = locationList.get(locationList.size() - 1);
                Log.i("MapsActivity", "Location: " + location.getLatitude() + " " + location.getLongitude());
                mLastLocation = location;
                if (mCurrLocationMarker != null) {
                    mCurrLocationMarker.remove();
                }
                //Place current location marker
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                //move map camera
                mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 10));
            }
        }
    };
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Location Permission Needed")
                        .setMessage("This app needs the Location permission, please accept to use location functionality")
                        .setPositiveButton("OK", (dialogInterface, i) -> {
                            //Prompt the user once explanation has been shown
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    MY_PERMISSIONS_REQUEST_LOCATION);
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_LOCATION) {// If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay! Do the
                // location-related task you need to do.
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                    mGoogleMap.setMyLocationEnabled(true);
                }
            } else {
                // permission denied, boo! Disable the
                // functionality that depends on this permission.
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
    private double radiansToDegrees(double x) {
        return x * 180.0 / Math.PI;
    }


}