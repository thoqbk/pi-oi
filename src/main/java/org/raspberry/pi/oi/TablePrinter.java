/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.raspberry.pi.oi;

import com.google.common.base.Strings;
import java.util.Formatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thoqbk
 */
public class TablePrinter {

    private static final Logger logger = LoggerFactory.getLogger(TablePrinter.class);

    private final int addressColumn = 20;
    private final int isAliveHostResponseColumn = 40;
    private final int shouldBeARPiColumn = 18;
    private final int timeColumn = 8;

    private final String splitter = Strings.repeat("-", addressColumn)
            + "-"
            + Strings.repeat("-", isAliveHostResponseColumn)
            + "-"
            + Strings.repeat("-", shouldBeARPiColumn)
            + "-"
            + Strings.repeat("-", timeColumn);

    public void printHeader() {
        logger.info("+" + splitter + "+");
        printRecord("Address", "Host response", "Could be a Pi", "Time");
        logger.info("+" + splitter + "+");
    }

    public void printFooter() {
        logger.info("+" + splitter + "+");
        logger.info("Tho Q Luong, http://github.com/thoqbk/pi-oi");
    }

    public void printRecord(String address, String isAliveHostResponse, boolean shouldBeARPi, int time) {
        printRecord(address, isAliveHostResponse, shouldBeARPi ? "YES" : "", "" + time + "s");
    }

    public void printRecord(String address, String isAliveHostResponse, String shouldBeARPi, String time) {
        Formatter formatter = new Formatter();

        String format = "| %-" + (addressColumn - 2) + "s "
                + "| %-" + (isAliveHostResponseColumn - 2) + "." + (isAliveHostResponseColumn - 5) + "s "
                + "| %-" + (shouldBeARPiColumn - 2) + "s "
                + "| %-" + (timeColumn - 2) + "s |";
        String message = formatter.format(format, address, isAliveHostResponse, shouldBeARPi, time)
                .toString();

        logger.info(message);
    }

    public void printRecord(String content) {
        Formatter formatter = new Formatter();
        int length = addressColumn + isAliveHostResponseColumn + shouldBeARPiColumn + timeColumn + 1;
        String format = "| %-" + length + "." + (length - 2) + "s |";
        String message = formatter.format(format, content)
                .toString();
        logger.info(message);
    }

}
