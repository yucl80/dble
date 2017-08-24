package io.mycat.manager.response;


import io.mycat.manager.ManagerConnection;
import io.mycat.net.mysql.OkPacket;
import io.mycat.statistic.stat.QueryConditionAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReloadQueryCf {
    private ReloadQueryCf() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ReloadSqlSlowTime.class);

    public static void execute(ManagerConnection c, String cf) {

        if (cf == null) {
            cf = "NULL";
        }

        QueryConditionAnalyzer.getInstance().setCf(cf);

        LOGGER.warn(String.valueOf(c) + "Reset show  @@sql.condition=" + cf + " success by manager");

        OkPacket ok = new OkPacket();
        ok.packetId = 1;
        ok.affectedRows = 1;
        ok.serverStatus = 2;
        ok.message = "Reset show  @@sql.condition success".getBytes();
        ok.write(c);
    }

}
