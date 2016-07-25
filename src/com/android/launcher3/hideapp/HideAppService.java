/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
 * Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
 copyright notice, this list of conditions and the following
 disclaimer in the documentation and/or other materials provided
 with the distribution.
 * Neither the name of The Linux Foundation nor the names of its
 contributors may be used to endorse or promote products derived
 from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.launcher3.hideapp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import android.util.Xml;

public class HideAppService {
    public static List<HideAppInfo> read(InputStream xml) throws Exception {
        List<HideAppInfo> hideinfos = null;
        HideAppInfo hideinfo = null;
        XmlPullParser pullParser = Xml.newPullParser();
        pullParser.setInput(xml, "UTF-8");
        int event = pullParser.getEventType();

        while (event != XmlPullParser.END_DOCUMENT) {
            switch (event) {
                case XmlPullParser.START_DOCUMENT:
                    hideinfos = new ArrayList<HideAppInfo>();
                    break;
                case XmlPullParser.START_TAG:
                    if ("app".equals(pullParser.getName())) {
                        hideinfo = new HideAppInfo();
                    }
                    if ("componentPackage".equals(pullParser.getName())) {
                        String pack = pullParser.nextText();
                        hideinfo.setComponentPackage(pack);
                    }
                    if ("componentClass".equals(pullParser.getName())) {
                        String cl = pullParser.nextText();
                        hideinfo.setComponentClass(cl);
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if ("app".equals(pullParser.getName())) {
                        hideinfos.add(hideinfo);
                        hideinfo = null;
                    }
                    break;
            }
            event = pullParser.next();
        }
        return hideinfos;
    }

    public static void save(List<HideAppInfo> infos, OutputStream out) throws Exception {
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(out, "UTF-8");
        serializer.startDocument("UTF-8", true);
        serializer.startTag(null, "apps");
        for (HideAppInfo info : infos) {
            serializer.startTag(null, "app");
            serializer.startTag(null, "componentPackage");
            serializer.text(info.getComponentPackage().toString());
            serializer.endTag(null, "componentPackage");
            serializer.startTag(null, "componentClass");
            serializer.text(info.getComponentClass().toString());
            serializer.endTag(null, "componentClass");
            serializer.endTag(null, "app");
        }
        serializer.endTag(null, "apps");
        serializer.endDocument();
        out.flush();
        out.close();
    }
}
