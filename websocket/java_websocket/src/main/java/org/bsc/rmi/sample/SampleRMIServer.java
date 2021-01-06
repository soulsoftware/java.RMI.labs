package org.bsc.rmi.sample;

import lombok.extern.slf4j.Slf4j;

import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

/**
 * Remote object to receive calls forwarded from the ServletHandler.
 */
@Slf4j
public class SampleRMIServer extends java.rmi.server.UnicastRemoteObject implements SampleRMI {

    public SampleRMIServer() throws RemoteException {
        super();
    }

    public String justPass(String passed) throws RemoteException {
        log.info( "justPass( '{}' )", passed );
        return format( "string passed to remote server is [%s]", passed) ;
    }

    @Override
    public String getInfo() throws RemoteException {
        return "I'm a RMI server";
    }

    /**
     * You should not need to run this server from the command line.
     * The ServletHandler class creates its own instance of the
     * rmiregistry and (optionally) an instance of this class as well.
     * This main method will not be executed from the ServletHandler.
     */


    private static CompletableFuture<Void> bind( Registry reg ) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            reg.bind( "SampleRMI", new SampleRMIServer() );
            result.complete( null );
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    private static CompletableFuture<Registry> createRMIRegistry(int port ) {

        final RMIServerSocketFactory serverSocketFactory = RMISocketFactory.getDefaultSocketFactory();
        final RMIClientSocketFactory clientSocketFactory = RMISocketFactory.getDefaultSocketFactory();

        CompletableFuture<Registry> result = new CompletableFuture<>();

        try {
            final Registry reg = java.rmi.registry.LocateRegistry.createRegistry(port, clientSocketFactory, serverSocketFactory );

            result.complete(reg);

        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }


    /**
     *
     * @param args
     */
    public static void main(String args[]) {

        log.info( format( "java.security.policy=[%s]", System.getProperty("java.security.policy")));

        createRMIRegistry(1099)
            .thenCompose( SampleRMIServer::bind )
            .exceptionally( ex -> {
                log.error("main", ex);
                return null;
            })
            .join();
    }
}