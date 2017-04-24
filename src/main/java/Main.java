import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.PemKeyCertOptions;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Main {

  public static void main(String[] args) throws Exception {

    SelfSignedCertificate cert = new SelfSignedCertificate("localhost");

    Vertx vertx = Vertx.vertx();

    vertx.createNetServer(new NetServerOptions()
        .setSsl(true).setKeyCertOptions(new PemKeyCertOptions()
            .setKeyPath(cert.privateKey().getAbsolutePath())
            .setCertPath(cert.certificate().getAbsolutePath()))).connectHandler(conn -> {
      System.out.println("connected");
    }).listen(1234, ar -> {
      if (ar.succeeded()) {
        connect(cert);
      } else {
        System.out.println("Could not bind server " + ar.cause().getMessage());
      }
    });

  }

  private static void connect(SelfSignedCertificate cert) {

    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(new NioEventLoopGroup());
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(new ChannelInitializer<Channel>() {
      @Override
      protected void initChannel(Channel ch) throws Exception {
        SslContext ctx = SslContextBuilder.forClient()
            .sslProvider(SslProvider.OPENSSL)
            .trustManager(cert.certificate()).build();
        SSLEngine engine = ctx.newEngine(ch.alloc(), "localhost", 1234);
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
