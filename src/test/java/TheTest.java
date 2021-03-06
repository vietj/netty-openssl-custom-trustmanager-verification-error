import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.Test;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TheTest {

  @Test
  public void doTest() throws Exception {

    CompletableFuture<Boolean> cf = new CompletableFuture<>();

    SelfSignedCertificate cert = new SelfSignedCertificate("localhost");

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.channel(NioServerSocketChannel.class);
    bootstrap.childHandler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        System.out.println("client has connected starting handshake");
        SslContext ctx = SslContextBuilder.forServer(cert.certificate(), cert.privateKey()).build();
        SslHandler handler = new SslHandler(ctx.newEngine(ch.alloc()));
        handler.handshakeFuture().addListener(fut -> {
          if (fut.isSuccess()) {
            System.out.println("connected to server");
          } else {
            System.out.println("handshake failed on server");
          }
        });
        ch.pipeline().addLast("ssl", handler);
      }
    });
    bootstrap.bind(1234).addListener(fut -> {
      if (fut.isSuccess()) {
        System.out.println("server started");
        connect(cert);
      } else {
        System.out.println("could not bind server " + fut.cause().getMessage());
      }
    });

    cf.get(10, TimeUnit.SECONDS);
  }

  private void connect(SelfSignedCertificate cert) {

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        System.out.println("client connected starting handshake");
        SslContext ctx = SslContextBuilder.forClient()

            // Switching from JDK to OPEN_SSL make the test pass

            .sslProvider(SslProvider.JDK)
            .trustManager(new TrustManagerFactory(new TrustManagerFactorySpi() {
              @Override
              protected void engineInit(KeyStore keyStore) throws KeyStoreException {
              }
              @Override
              protected TrustManager[] engineGetTrustManagers() {
                // Provide a custom trust manager, this manager trust all certificates
                return new TrustManager[]{
                    new X509TrustManager() {
                      @Override
                      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                      }
                      @Override
                      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                      }
                      @Override
                      public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                      }
                    }
                };
              }
              @Override
              protected void engineInit(ManagerFactoryParameters managerFactoryParameters) throws InvalidAlgorithmParameterException {
              }
            }, new Provider("", 0.0, "") {
            }, "") {
            }).build();
        SSLEngine engine = ctx.newEngine(ch.alloc(), "localhost_", 1234);
        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(sslParameters);
        SslHandler sslHandler = new SslHandler(engine);
        sslHandler.handshakeFuture().addListener(fut -> {
          System.out.println("Handshake : " + fut.isSuccess());
        });
        ch.pipeline().addLast("ssl", sslHandler);
      }
    });

    bootstrap.connect("localhost", 1234);


  }
}
