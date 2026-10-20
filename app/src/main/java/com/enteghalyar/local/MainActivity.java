package com.enteghalyar.local;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private LocalServer server;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        TextView urlView=findViewById(R.id.url);
        TextView status=findViewById(R.id.status);
        ImageView qr=findViewById(R.id.qr);
        String ip=getLocalIpv4();
        String url="http://"+ip+":8080/join";
        try {
            qr.setImageBitmap(makeQr(url,700));
            urlView.setText(url);
            server=new LocalServer(8080,getExternalFilesDir(null));
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT,false);
            status.setText("● آماده است — گوشی دوم QR را اسکن کند");
        } catch(Exception e) {
            status.setText("خطا در اجرای سرویس محلی: "+e.getClass().getSimpleName());
        }
    }
    private Bitmap makeQr(String text,int size)throws Exception {
        Map<EncodeHintType,Object> hints=new HashMap<>();
        hints.put(EncodeHintType.MARGIN,1);
        BitMatrix m=new MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size,hints);
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);
        return b;
    }
    private String getLocalIpv4() {
        try {
            for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces()))
                for(InetAddress a:Collections.list(ni.getInetAddresses())) {
                    String ip=a.getHostAddress();
                    if(!a.isLoopbackAddress() && ip.indexOf(':')<0) return ip;
                }
        } catch(Exception ignored) {}
        return "192.168.43.1";
    }
    @Override protected void onDestroy() { if(server!=null) server.stop(); super.onDestroy(); }

    static class LocalServer extends NanoHTTPD {
        private final File root;
        LocalServer(int port,File root) { super(port); this.root=root; if(!root.exists())root.mkdirs(); }
        @Override public Response serve(IHTTPSession s) {
            try {
                String path=s.getUri();
                if("/join".equals(path)||"/".equals(path)) return html();
                if("/api/upload".equals(path)&&Method.POST.equals(s.getMethod())) {
                    Map<String,String> h=s.getHeaders();
                    String raw=h.get("x-filename");
                    String name=raw==null?"file-"+System.currentTimeMillis():URLDecoder.decode(raw,"UTF-8");
                    name=new File(name).getName().replaceAll("[\\\\/:*?\"<>|]","_");
                    File out=new File(root,System.currentTimeMillis()+"_"+name);
                    InputStream in=s.getInputStream();
                    String cl=h.get("content-length");
                    long expected=0; try { if(cl!=null)expected=Long.parseLong(cl); } catch(Exception ignored) {}
                    try(FileOutputStream fos=new FileOutputStream(out)) {
                        byte[] buf=new byte[64*1024]; int n; long total=0;
                        while((n=in.read(buf))!=-1) { fos.write(buf,0,n); total+=n; if(expected>0&&total>=expected)break; }
                    }
                    return newFixedLengthResponse(Response.Status.OK,"application/json; charset=utf-8",
                        "{\"ok\":true,\"name\":\""+esc(name)+"\",\"size\":"+out.length()+"}");
                }
                return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain; charset=utf-8","Not Found");
            } catch(Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain; charset=utf-8","Server error");
            }
        }
        private Response html() {
            try {
                InputStream in=getClass().getResourceAsStream("/web/index.html");
                if(in==null)return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain","web missing");
                byte[] buf=new byte[8192]; StringBuilder b=new StringBuilder(); int n;
                while((n=in.read(buf))!=-1)b.append(new String(buf,0,n,"UTF-8"));
                return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8",b.toString());
            } catch(Exception e) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain","web error");
            }
        }
        private static String esc(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    }
}