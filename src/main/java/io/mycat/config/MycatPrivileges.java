/*
 * Copyright (c) 2013, OpenCloudDB/MyCAT and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software;Designed and Developed mainly by many Chinese
 * opensource volunteers. you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 2 only, as published by the
 * Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Any questions about this component can be directed to it's project Web address
 * https://code.google.com/p/opencloudb/.
 *
 */
package io.mycat.config;

import com.alibaba.druid.wall.WallCheckResult;
import com.alibaba.druid.wall.WallProvider;
import io.mycat.MycatServer;
import io.mycat.config.model.FirewallConfig;
import io.mycat.config.model.UserConfig;
import io.mycat.config.model.UserPrivilegesConfig;
import io.mycat.net.handler.FrontendPrivileges;
import io.mycat.server.ServerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author mycat
 */
public class MycatPrivileges implements FrontendPrivileges {
    /**
     * 无需每次建立连接都new实例。
     */
    private static MycatPrivileges instance = new MycatPrivileges();

    private static final Logger ALARM = LoggerFactory.getLogger("alarm");

    private boolean check = false;
    private static final ThreadLocal<WallProvider> CONTEXT_LOCAL = new ThreadLocal<>();

    public static MycatPrivileges instance() {
        return instance;
    }

    protected MycatPrivileges() {
        super();
    }

    @Override
    public boolean schemaExists(String schema) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        return conf.getSchemas().containsKey(schema);
    }

    @Override
    public boolean userExists(String user, String host) {
        //检查用户及白名单
        return checkFirewallWhiteHostPolicy(user, host);
    }

    @Override
    public String getPassword(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        if (user != null && user.equals(conf.getSystem().getClusterHeartbeatUser())) {
            return conf.getSystem().getClusterHeartbeatPass();
        } else {
            UserConfig uc = conf.getUsers().get(user);
            if (uc != null) {
                return uc.getPassword();
            } else {
                return null;
            }
        }
    }

    @Override
    public Set<String> getUserSchemas(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        UserConfig uc = conf.getUsers().get(user);
        if (uc != null) {
            return uc.getSchemas();
        } else {
            return null;
        }
    }

    @Override
    public Boolean isReadOnly(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        UserConfig uc = conf.getUsers().get(user);
        Boolean result = null;
        if (uc != null) {
            result = uc.isReadOnly();
        }
        return result;
    }

    @Override
    public int getBenchmark(String user) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        UserConfig uc = conf.getUsers().get(user);
        if (uc != null) {
            return uc.getBenchmark();
        } else {
            return 0;
        }
    }

    protected boolean checkManagerPrivilege(String user) {
        //  normal user don't neet manager privilege
        return true;
    }

    @Override
    public boolean checkFirewallWhiteHostPolicy(String user, String host) {

        MycatConfig config = MycatServer.getInstance().getConfig();
        FirewallConfig firewallConfig = config.getFirewall();

        if (!checkManagerPrivilege(user)) {
            // return and don't trigger firewall alarm
            return false;
        }


        //防火墙 白名单处理
        boolean isPassed = false;

        Map<String, List<UserConfig>> whitehost = firewallConfig.getWhitehost();
        if (whitehost == null || whitehost.size() == 0) {
            Map<String, UserConfig> users = config.getUsers();
            isPassed = users.containsKey(user);

        } else {
            List<UserConfig> list = whitehost.get(host);
            if (list != null) {
                for (UserConfig userConfig : list) {
                    if (userConfig.getName().equals(user)) {
                        isPassed = true;
                        break;
                    }
                }
            }
        }

        if (!isPassed) {
            ALARM.error(Alarms.FIREWALL_ATTACK + "[host=" + host +
                    ",user=" + user + ']');
            return false;
        }
        return true;
    }


    /**
     * @see https://github.com/alibaba/druid/wiki/%E9%85%8D%E7%BD%AE-wallfilter
     */
    @Override
    public boolean checkFirewallSQLPolicy(String user, String sql) {

        boolean isPassed = true;

        if (CONTEXT_LOCAL.get() == null) {
            FirewallConfig firewallConfig = MycatServer.getInstance().getConfig().getFirewall();
            if (firewallConfig != null) {
                if (firewallConfig.isCheck()) {
                    CONTEXT_LOCAL.set(firewallConfig.getProvider());
                    check = true;
                }
            }
        }

        if (check) {
            WallCheckResult result = CONTEXT_LOCAL.get().check(sql);
            if (!result.getViolations().isEmpty()) {
                isPassed = false;
                ALARM.warn("Firewall to intercept the '" + user + "' unsafe SQL , errMsg:" +
                        result.getViolations().get(0).getMessage() + " \r\n " + sql);
            }
        }
        return isPassed;
    }

    public enum Checktype {
        INSERT, UPDATE, SELECT, DELETE
    }

    // 审计SQL权限
    public static boolean checkPrivilege(ServerConnection source, String schema, String tableName, Checktype chekctype) {
        MycatConfig conf = MycatServer.getInstance().getConfig();
        UserConfig userConfig = conf.getUsers().get(source.getUser());
        if (userConfig == null) {
            return true;
        }
        UserPrivilegesConfig userPrivilege = userConfig.getPrivilegesConfig();
        if (userPrivilege == null || !userPrivilege.isCheck()) {
            return true;
        }
        UserPrivilegesConfig.SchemaPrivilege schemaPrivilege = userPrivilege.getSchemaPrivilege(schema);
        if (schemaPrivilege == null) {
            return true;
        }
        UserPrivilegesConfig.TablePrivilege tablePrivilege = schemaPrivilege.getTablePrivilege(tableName);
        if (tablePrivilege == null && schemaPrivilege.getDml().length == 0) {
            return true;
        }
        int index = -1;
        if (chekctype == Checktype.INSERT) {
            index = 0;
        } else if (chekctype == Checktype.UPDATE) {
            index = 1;
        } else if (chekctype == Checktype.SELECT) {
            index = 2;
        } else if (chekctype == Checktype.DELETE) {
            index = 3;
        }
        if (tablePrivilege != null) {
            return tablePrivilege.getDml()[index] > 0;
        } else {
            return schemaPrivilege.getDml()[index] > 0;
        }
    }
}
