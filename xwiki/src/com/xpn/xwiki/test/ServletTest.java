/**
 * User: ludovic
 * Date: 13 mars 2004
 * Time: 15:04:19
 * ===================================================================
 *
 * Copyright (c) 2003 Ludovic Dubost, All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details, published at
 * http://www.gnu.org/copyleft/lesser.html or in lesser.txt in the
 * root folder of this distribution.
 *
 */

package com.xpn.xwiki.test;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.store.XWikiStoreInterface;
import org.apache.cactus.ServletTestCase;
import org.apache.cactus.WebRequest;
import org.apache.struts.action.ActionServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;


public abstract class ServletTest extends ServletTestCase {
    public String hibpath = "hibernate-test.cfg.xml";
    public XWikiContext context = new XWikiContext();
    public XWiki xwiki;

    public void setUp() throws Exception {
        super.setUp();
        flushCache();
    };

    public void cleanUp() {
    };

    public void clientSetUp(XWikiStoreInterface store) throws XWikiException {
        xwiki = new XWiki("./xwiki.cfg", context);
        context.setWiki(xwiki);
    }


    public static void setUrl(WebRequest webRequest, String action, String docname) {
        setUrl(webRequest, action, docname, "");
    }

    public static void setUrl(WebRequest webRequest, String action, String docname, String query) {
        webRequest.setURL("127.0.0.1:9080", "/xwiki" , "/testbin", "/" + action + "/Main/" + docname, query);
    }

    public static void setVirtualUrl(WebRequest webRequest, String host, String appname, String action, String docname, String query) {
        webRequest.setURL(host + ":9080", "/" + appname , "/testbin", "/" + action + "/Main/" + docname, query);
    }

    public String getHibpath() {
        // Usefull in case we need to understand where we are
        String path = (new File(".")).getAbsolutePath();
        System.out.println("Current Directory is: " + path);

        File file = new File(hibpath);
        if (file.exists())
            return hibpath;

        file = new File("WEB-INF", hibpath);
        if (file.exists())
            return "./WEB-INF/" + hibpath;

        file = new File("test", hibpath);
        if (file.exists())
            return "./test/" + hibpath;

        if (config!=null)
        {
            ServletContext context = config.getServletContext();
            if (context!=null)
                return context.getRealPath("WEB-INF/" + hibpath);
        }

        return hibpath;
    }

    public void cleanSession(HttpSession session) {
        Vector names = new Vector();
        Enumeration enum = session.getAttributeNames();
        while (enum.hasMoreElements()) {
            String name = (String) enum.nextElement();
            names.add(name);
        }

        for (int i=0;i<names.size();i++)
        {
            session.removeAttribute((String)names.get(i));
        }
    }

    public void flushCache() {
        // We need to flush the server cache before running our tests
        // because we are modifiying the database behind the scenes
        // so if we are running the tests twice we won't necessarly
        // get the same results..
        try {
            XWiki xwiki = (XWiki) config.getServletContext().getAttribute("xwikitest");
            if (xwiki!=null)
                xwiki.flushCache();
            xwiki = (XWiki) config.getServletContext().getAttribute("xwiki");
            if (xwiki!=null)
             xwiki.flushCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void launchTest() throws Throwable {
        try {
            ActionServlet servlet = new ActionServlet();
            servlet.init(config);
            servlet.service(request, response);
            cleanSession(session);
        } catch (ServletException e) {
            e.getRootCause().printStackTrace();
            throw e.getRootCause();
        }
    }

}
