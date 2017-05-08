package cn.wjh1119.simplesocket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Enumeration;
import java.util.HashMap;

import static android.R.attr.port;

public class MainActivity extends AppCompatActivity {

    // UI references.
    private AutoCompleteTextView mEmailView;
    private EditText mPasswordView;
    private TextView mMessageView;

    private final static int MSG_CLIENT_RECIEVE = 0;
    private final static int MSG_CLIENT_SEND = 1;
    private final static int MSG_SERVER_REVIECE = 2;
    private final static int MSG_SERVER_SEND = 3;
    private final static int MSG_ERROR = 4;
    private final static int MSG_CLIENT = 5;
    private final static int MSG_SERVER = 6;

    private static int Port = 32100;

    private MessageHandler myHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the login form.
        mEmailView = (AutoCompleteTextView) findViewById(R.id.email);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == R.id.login || id == EditorInfo.IME_NULL) {
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String emailText = mEmailView.getText().toString();
                String pswText = mPasswordView.getText().toString();

                //开启Login线程
                new LoginThread(emailText, pswText).start();
            }
        });

        mMessageView = (TextView) findViewById(R.id.message);

        myHandler = new MessageHandler(this);

        //开启服务器线程
        ServerThread serverThread = new ServerThread();
        serverThread.start();
    }

    private class LoginThread extends Thread {

        private HashMap<String, String> loginInfo = new HashMap<>();

        public LoginThread(String email, String psw) {
            this.loginInfo.put("email", email);
            this.loginInfo.put("password", psw);
        }

        @Override
        public void run() {
            try {
                String host = getInetIpAddress();
                Socket socket = new Socket();
                Log.d("client socket", "host is " + host + " port is " + Port);
                socket.connect(new InetSocketAddress(host, Port), 5000);
//                socket.connect(new InetSocketAddress("192.168.1.113", Port), 5000);
                myHandler.obtainMessage(MSG_CLIENT, "create a socket,port is " + Port).sendToTarget();

                PrintWriter printWriter = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));

                //发出数据
                printWriter.println("login " +
                        loginInfo.get("email") + " " + loginInfo.get("password"));
                myHandler.obtainMessage(MSG_CLIENT_SEND, "login " +
                        loginInfo.get("email") + " " + loginInfo.get("password")).sendToTarget();


                //获取数据
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    myHandler.obtainMessage(MSG_CLIENT_RECIEVE, line).sendToTarget();
                }
                //关闭各种输入输出流
                bufferedReader.close();
                printWriter.close();
                socket.close();
            } catch (SocketTimeoutException e) {
                String error = "服务器连接失败！请检查网络是否打开";
                myHandler.obtainMessage(MSG_ERROR, error).sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //创建一个服务器线程在后台监听
    private class ServerThread extends Thread {
        private boolean isCreate = false;
        ServerSocket serverSocket = null;

        @Override
        public void run() {

            while (!isCreate) {
                try {
                    // 创建一个serversocket对象，并让他在Port端口监听
                    serverSocket = new ServerSocket(Port);
                    myHandler.obtainMessage(MSG_SERVER, "Port is " + Port).sendToTarget();
                    isCreate = true;
                    while (true) {
                        // 调用serversocket的accept()方法，接收客户端发送的请求
                        myHandler.obtainMessage(MSG_SERVER_SEND, "wait connection").sendToTarget();
                        Socket socket = serverSocket.accept();
                        myHandler.obtainMessage(MSG_SERVER_SEND, "accept").sendToTarget();
                        BufferedWriter bufferedWriter = new BufferedWriter(
                                new OutputStreamWriter(socket.getOutputStream()));
                        BufferedReader buffer = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));

                        // 读取数据
                        String msg = "";
                        while ((msg = buffer.readLine()) != null) {
                            myHandler.obtainMessage(MSG_SERVER_REVIECE, msg).sendToTarget();
                            String[] ss = msg.split(" ");
                            if (ss[0].equals("login")) {
                                String email = ss[1];
                                String password = ss[2];
                                if (email.equals("aaa") && password.equals("bbb")) {
                                    bufferedWriter.write("OK\n");
                                    bufferedWriter.flush();
                                    myHandler.obtainMessage(MSG_SERVER_SEND, "OK").sendToTarget();
                                } else {
                                    bufferedWriter.write("Wrong email or password, please try\n");
                                    bufferedWriter.flush();
                                    myHandler.obtainMessage(MSG_SERVER_SEND, "WRONG").sendToTarget();
                                }
                            } else {
                                bufferedWriter.write("Fail\n");
                                bufferedWriter.flush();
                                myHandler.obtainMessage(MSG_SERVER_SEND, "FAIL").sendToTarget();
                            }
                            break;
                        }

                    }

                } catch (BindException e) {
                    //当端口被占用了，使用下一个端口
                    myHandler.obtainMessage(MSG_SERVER, "port " + port + " is already in use.use next port: "
                            + ++Port).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    myHandler.obtainMessage(MSG_SERVER, "stop").sendToTarget();
                    try {
                        if (serverSocket != null) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //获取Ip地址
    public String getInetIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> ipAddr = intf.getInetAddresses(); ipAddr
                        .hasMoreElements();) {
                    InetAddress inetAddress = ipAddr.nextElement();
                    return inetAddress.getHostAddress();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static class MessageHandler extends Handler {
        private WeakReference<MainActivity> mActivityWR;

        MessageHandler(MainActivity activity) {
            mActivityWR = new WeakReference<>(activity);
        }
        @Override
        public void handleMessage(Message msg) {
            final MainActivity mainActivity = mActivityWR.get();
            TextView mMessageView = mainActivity.mMessageView;
            switch (msg.what) {
                case MSG_CLIENT_RECIEVE:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("client receive:" + msg.obj + "\n");
                    }
                    break;
                case MSG_CLIENT_SEND:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("client send:" + msg.obj + "\n");
                    }
                    break;
                case MSG_SERVER_REVIECE:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("server receive:" + msg.obj + "\n");
                    }
                    break;
                case MSG_SERVER_SEND:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("server send:" + msg.obj + "\n");
                    }
                    break;
                case MSG_ERROR:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("error:" + msg.obj + "\n");
                    }
                    break;
                case MSG_CLIENT:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("client:" + msg.obj + "\n");
                    }
                    break;
                case MSG_SERVER:
                    if (null != mMessageView && null != msg.obj) {
                        mMessageView.append("server:" + msg.obj + "\n");
                    }
                    break;

            }

        }
    }
}

