/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberry.pi.oi;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thoqbk
 */
public class RPiScanner {

    //--------------------------------------------------------------------------
    //  Members
    private static final Logger logger = LoggerFactory.getLogger(RPiScanner.class);

    private static final int RANGE_LENGTH = 5;

    private final String networkPrefix;
    private final int initPivot;

    private final List<Integer> pendingHostNumbers = new ArrayList<>();

    private final TablePrinter tablePrinter;

    private long startTime = 0;

    private int sshPort = 22;
    private long readTimeout = 1000;//ms

    private long openConnectionTimeout = 500;
    
    private int rpiCount = 0;

    public RPiScanner(String networkPrefix, int initPivot, int lowerPivot, int upperPivot, TablePrinter tablePrinter) {
        this.networkPrefix = networkPrefix;
        this.initPivot = initPivot;
        this.tablePrinter = tablePrinter;
        for (int hostNumber = lowerPivot; hostNumber <= upperPivot; hostNumber++) {
            pendingHostNumbers.add(hostNumber);
        }
        //debug
        logger.debug("Create scanner with: lowerPivot: " + lowerPivot + ", upperPivot: " + upperPivot + " and initPivot: " + initPivot);
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public void setReadTimeout(long readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public void setOpenConnectionTimeout(long timeout){
        this.openConnectionTimeout = timeout;
    }

    public int getRpiCount() {
        return rpiCount;
    }
    
    

    public void scan() {
        int pivot = -1;
        List<Integer> range = null;
        while (!pendingHostNumbers.isEmpty() || range != null || pivot != -1) {
            if (range == null && pivot == -1) {
                range = pickRange(initPivot);
            } else if (range != null && !range.isEmpty()) {
                boolean firstHostIsAlive = false;
                boolean lastHostIsAlive = false;
                for (int idx = 0; idx < range.size(); idx++) {
                    int hostNumber = range.get(idx);
                    String response = checkHost(hostNumber);
                    if (isAliveHostResponse(response)) {
                        if (idx == 0) {
                            firstHostIsAlive = true;
                        }
                        if (idx == range.size() - 1) {
                            lastHostIsAlive = true;
                        }
                    }
                }
                if (firstHostIsAlive) {
                    range = pickRange(range.get(0), false);
                    pivot = -1;
                } else if (lastHostIsAlive) {
                    range = pickRange(range.get(range.size() - 1), true);
                    pivot = -1;
                } else {
                    range = null;
                    pivot = pickPivot();
                }
            } else if (pivot != -1) {
                String response = checkHost(pivot);
                if (isAliveHostResponse(response)) {
                    range = pickRange(pivot);
                    pivot = -1;
                } else {
                    pivot = pickPivot();
                }
            } else {
                pivot = pickPivot();
                range = null;
            }
        }
    }

    private List<Integer> pickRange(int from, boolean right) {
        logger.debug("Begin allocating range: from: " + from + ", length: " + RANGE_LENGTH);
        List<Integer> retVal = new ArrayList<>();
        if (pendingHostNumbers.isEmpty()) {
            return retVal;
        }
        //ELSE:        
        int minHostNumber = pendingHostNumbers.get(0);
        int maxHostNumber = pendingHostNumbers.get(pendingHostNumbers.size() - 1);
        int hostNumber = from;
        while (retVal.size() < RANGE_LENGTH && hostNumber <= maxHostNumber && hostNumber >= minHostNumber) {
            if (pendingHostNumbers.contains(hostNumber)) {
                retVal.add(hostNumber);
            }
            hostNumber += (right ? 1 : -1);
        }
        pendingHostNumbers.removeAll(retVal);

        logger.debug("Picked range: " + retVal);

        return retVal;
    }

    private List<Integer> pickRange(int from) {
        if (pendingHostNumbers.isEmpty()) {
            return new ArrayList<>();
        }
        //ELSE:
        int fromIdx = pendingHostNumbers.indexOf(from);
        boolean right = fromIdx >= pendingHostNumbers.size() / 2 || from < pendingHostNumbers.get(0);
        return pickRange(from, right);
    }

    private int pickPivot() {
        int retVal = -1;
        if (pendingHostNumbers.isEmpty()) {
            return retVal;
        }
        if (pendingHostNumbers.contains(initPivot)) {
            retVal = initPivot;
        } else {
            List<List<Integer>> ranges = new ArrayList<>();
            List<Integer> currentRange = null;
            for (int idx = 0; idx < pendingHostNumbers.size(); idx++) {
                if (idx == 0) {
                    currentRange = new ArrayList<>();
                    ranges.add(currentRange);
                    currentRange.add(pendingHostNumbers.get(idx));
                } else if (pendingHostNumbers.get(idx) - 1 != pendingHostNumbers.get(idx - 1)) {
                    currentRange = new ArrayList<>();
                    ranges.add(currentRange);
                    currentRange.add(pendingHostNumbers.get(idx));
                } else {
                    currentRange.add(pendingHostNumbers.get(idx));
                }
            }
            Collections.sort(ranges, new Comparator<List<Integer>>() {
                @Override
                public int compare(List<Integer> o1, List<Integer> o2) {
                    int retVal = 0;
                    if (o1.size() > o2.size()) {
                        retVal = -1;
                    } else if (o1.size() < o2.size()) {
                        retVal = 1;
                    }
                    return retVal;
                }
            });
            retVal = ranges.get(0).get(ranges.get(0).size() / 2);
        }
        pendingHostNumbers.removeAll(Lists.newArrayList(retVal));

        logger.debug("Picked pivot: " + retVal);

        return retVal;
    }

    private String checkHost(int hostNumber) {
        final String address = networkPrefix + "." + hostNumber;
        final Socket socket = new Socket();
        final StringBuilder serverResponse = new StringBuilder();

        Thread openConnectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket.connect(new InetSocketAddress(address, sshPort));
                    logger.debug("Connected to " + address);
                } catch (IOException ex) {
                    //logger.debug(null, ex);
                }
            }
        });
        openConnectionThread.setDaemon(true);
        openConnectionThread.start();
        try {
            Thread.sleep(openConnectionTimeout);
            openConnectionThread.interrupt();
            if (socket.isConnected()) {
                Thread readThread = new Thread(new Runnable() {
                    @Override
                    public void run() {                        
                        try {
                            socket.getOutputStream().write("hello".getBytes());
                            boolean flag = true;
                            while (flag) {
                                int readByte = socket.getInputStream().read();
                                if (readByte != -1) {
                                    char ch = (char) readByte;
                                    boolean b1 = 'a' <= ch && ch <= 'z';
                                    boolean b2 = 'A' <= ch && ch <= 'Z';
                                    boolean b3 = '0' <= ch && ch <= '9';
                                    boolean b4 = Arrays.asList(' ', '.', '-', ',', '_').contains(ch);
                                    if (b1 || b2 || b3 || b4) {
                                        serverResponse.append(ch);
                                    }
                                } else {
                                    flag = false;
                                }
                            }
                        } catch (IOException ex) {
                            //logger.debug(null, ex);
                        }
                    }

                });
                readThread.setDaemon(true);
                readThread.start();
                Thread.sleep(readTimeout);
                readThread.interrupt();
                socket.close();
            }            
        } catch (InterruptedException | IOException ex) {
            logger.debug(null, ex);
        }
        String retVal = serverResponse.toString();
        if (retVal != null && retVal.length() > 0) {
            logger.debug("Response from host: " + address + ": " + retVal);
        }
        log(address, retVal);
        //return
        return retVal;
    }

    private boolean isAliveHostResponse(String response) {
        if (response == null || response.length() == 0) {
            return false;
        }
        String pattern = "[a-z0-9]+";
        return Pattern.compile(pattern).matcher(response).find();
    }

    private boolean shouldBeARPi(String response) {
        if (!isAliveHostResponse(response)) {
            return false;
        }
        return response.toLowerCase().contains("debian");
    }

    private void log(String host, String hostResponse) {
        boolean isAliveHostResponse = isAliveHostResponse(hostResponse);
        if (!isAliveHostResponse) {
            return;
        }
        boolean shouldBeARPi = shouldBeARPi(hostResponse);        
        
        float time = (float)(System.currentTimeMillis() - startTime);

        int timeInSeconds = Math.round(time / 1000);

        tablePrinter.printRecord(host, hostResponse, shouldBeARPi, timeInSeconds);
        rpiCount++;
    }

}
