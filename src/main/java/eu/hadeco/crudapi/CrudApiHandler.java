/*
 *  Copyright (c) 2017. I.Kolchagov, All rights reserved.
 *  Contact: I.Kolchagov (kolchagov (at) gmail.com)
 *
 *  The contents of this file is licensed under the terms of LGPLv3 license.
 *  You may read the the included file 'lgpl-3.0.txt'
 *  or https://www.gnu.org/licenses/lgpl-3.0.txt
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 *  for the specific language governing rights and limitations under the License.
 *
 *  The project uses 'fluentsql' internally, licensed under Apache Public License v2.0.
 *  https://github.com/ivanceras/fluentsql/blob/master/LICENSE.txt
 *
 */

package eu.hadeco.crudapi;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.regex.Pattern;

import static eu.hadeco.crudapi.ApiConfig.MYSQL;

class CrudApiHandler extends AbstractHandler {
    static final Pattern TAG_FILTER = Pattern.compile("(<script>|</script>)");
//    private final ApiConfig apiConfig;

    private CrudApiHandler() throws IOException {
        //this is configuration example from tests!

    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects.
     * @throws java.lang.Exception if any.
     */
    public static void main(String[] args) throws Exception {
        HttpConfiguration config = new HttpConfiguration();
        config.setSendServerVersion(false);
        HttpConnectionFactory factory = new HttpConnectionFactory(config);
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, factory);
        server.setConnectors(new Connector[]{connector});
        connector.setHost("0.0.0.0");
        connector.setPort(8080);
        server.addConnector(connector);
        CrudApiHandler crudApiHandler = new CrudApiHandler();
        server.setHandler(crudApiHandler);
        server.start();
        server.join();
    }

    /** {@inheritDoc} */
    @Override
    public void handle(String target, Request baseReq, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        String user = baseReq.getHeader("user");
        String password = baseReq.getHeader("password");
        String dbName = baseReq.getHeader("db_name");
        String hostname = baseReq.getHeader("hostname");
        String datasource = MYSQL;
        try {
            datasource = ApiConfig.class.getDeclaredField(baseReq.getHeader("datasource")).get(null).toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        ApiConfig apiConfig = createApiConf(user, password, dbName, hostname, datasource);
        RequestHandler.handle(req, resp, apiConfig);
        baseReq.setHandled(true);
    }

    private ApiConfig createApiConf(String user, String password, String databaseName, String serverHostName, String datasourceClassName){
        return new ApiConfig(user,password, databaseName, serverHostName, datasourceClassName) {
            @Override
            public boolean columnAuthorizer(RequestHandler.Actions action, String database, String table, String column) {
                return !("password".equals(column) && RequestHandler.Actions.LIST.equals(action));
            }

            @Override
            public String[] recordFilter(RequestHandler.Actions action, String database, String table) {
                return "posts".equals(table) ? new String[]{"id,neq,13"} : null;
            }

            @Override
            public Object tenancyFunction(RequestHandler.Actions action, String database, String table, String column) {
                return "users".equals(table) && "id".equals(column) ? 1 : null;
            }

            @Override
            public Object inputSanitizer(RequestHandler.Actions action, String database, String table, String column, String type, Object value, HttpServletRequest context) {
                return value instanceof String ? TAG_FILTER.matcher(((String) value)).replaceAll("") : value;
            }

            @Override
            public Object inputValidator(RequestHandler.Actions action, String database, String table, String column, String type, Object value, HttpServletRequest context) {
//                    ($column=='category_id' && !is_numeric($value))?'must be numeric':true;
                return "category_id".equals(column) && !(value instanceof Long) ? "must be numeric" : true;
            }

            @Override
            public RequestHandler.Actions before(RequestHandler.Actions action, String database, String table, String[] ids, Map<String, Object> input) {
                if ("products".equals(table)) {
                    if (action == RequestHandler.Actions.CREATE) {
                        input.put("created_at", "2013-12-11 10:09:08");
                    } else if (action == RequestHandler.Actions.DELETE) {
                        action = RequestHandler.Actions.UPDATE;
                        input.put("deleted_at", "2013-12-11 11:10:09");
                    }
                }
                return action;
            }
        };
    }
}
