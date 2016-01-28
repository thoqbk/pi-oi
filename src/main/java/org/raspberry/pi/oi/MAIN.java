/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberry.pi.oi;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thoqbk
 */
public class MAIN {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MAIN.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        PropertyConfigurator.configure(MAIN.class.getResource("/org/raspberry/pi/oi/resource/log4j.properties"));

        int sshPort = getArgument(args, "sshPort", 22);
        long readTimeout = getArgument(args, "readTimeout", 1000);
        long openConnectionTimeout = getArgument(args, "openConnectionTimeout", 800);

        logger.info("");
        logger.info("PI-OI is finding your Pi ...");

        final AtomicInteger rpiCount = new AtomicInteger(0);

        //MAIN main = new MAIN();
        //main.scan(main.getLocalAddresses().get(0));
        int workersNumber = 10;
        int minHostNumber = 1;
        int maxHostNumber = 255;
        
        long startTime = System.currentTimeMillis();
        
        TablePrinter printer = new TablePrinter();

        List<String> localAddresses = MAIN.getLocalAddresses();

        printer.printHeader();

        for (String localAddress : localAddresses) {

            int startHostNumberIdx = localAddress.lastIndexOf('.') + 1;
            String networkPrefix = localAddress.substring(0, startHostNumberIdx - 1);
            int initHostNumber = Integer.parseInt(localAddress.substring(startHostNumberIdx));

            ExecutorService executorService = Executors.newFixedThreadPool(workersNumber);

            int step = (maxHostNumber - minHostNumber + 1) / workersNumber;
            for (int idx = 0; idx < workersNumber; idx++) {
                int lowerPivot = idx * step;
                int upperPivot = workersNumber - 1 == idx ? maxHostNumber : lowerPivot + step - 1;
                int initPivot = (lowerPivot <= initHostNumber && initHostNumber <= upperPivot) ? initHostNumber : -1;
                final RPiScanner scanner = new RPiScanner(networkPrefix, initPivot, lowerPivot, upperPivot, printer);
                scanner.setSshPort(sshPort);
                scanner.setReadTimeout(readTimeout);
                scanner.setOpenConnectionTimeout(openConnectionTimeout);
                scanner.setStartTime(startTime);
                
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        scanner.scan();
                        rpiCount.addAndGet(scanner.getRpiCount());
                        logger.debug("DONE");
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(10L, TimeUnit.MINUTES);
        }
        if (rpiCount.get() == 0) {
            printer.printRecord("(Pi not found)");
        }
        printer.printFooter();
        System.in.read();
    }

    private static int getArgument(String[] args, String argName, int defaultValue) {
        int retVal = defaultValue;
        List<String> args2 = Arrays.asList(args);
        int indexOfArgName = args2.indexOf("-" + argName);
        if (indexOfArgName != -1) {
            if (indexOfArgName + 1 > args2.size() - 1) {
                throw new RuntimeException("Invalid " + argName + " value");
            }
            try {
                retVal = Integer.parseInt(args2.get(indexOfArgName + 1));
            } catch (Exception ex) {
                throw new RuntimeException("Invalid " + argName + " value: " + args2.get(indexOfArgName + 1));
            }
        }
        return retVal;
    }

    private static List<String> getLocalAddresses() {
        List<String> retVal = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nwInterfaces = NetworkInterface.getNetworkInterfaces();
            while (nwInterfaces.hasMoreElements()) {
                NetworkInterface nwInterface = nwInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = nwInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    String address = inetAddresses.nextElement().getHostAddress();
                    if (isValidLocalAddress(address)) {
                        retVal.add(address);
                        //debug
                        logger.debug("Found local address: " + address);
                    }
                }
            }
        } catch (SocketException ex) {
            Logger.getLogger(MAIN.class.getName()).log(Level.SEVERE, null, ex);
        }
        //Sort
        Collections.sort(retVal, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                String[] o1s = o1.split("\\.");
                String[] o2s = o2.split("\\.");
                int i1 = Integer.parseInt(o1s[o1s.length - 2]);
                int i2 = Integer.parseInt(o2s[o2s.length - 2]);
                int retVal = 0;
                if (i1 > i2) {
                    retVal = 1;
                } else if (i1 < i2) {
                    retVal = -1;
                }
                return retVal;
            }
        });
        return retVal;
    }

    private static boolean isValidLocalAddress(String address) {
        String pattern = "^(192|10)\\.\\d+\\.\\d+\\.\\d+$";
        return Pattern.matches(pattern, address);
    }

}
