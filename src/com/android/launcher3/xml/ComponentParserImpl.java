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

package com.android.launcher3.xml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;

import android.util.Xml;

public class ComponentParserImpl {

    public List<ComponentInfo> parse(InputStream is) throws Exception {
        List<ComponentInfo> list = null;
        ComponentInfo componentInfo = null;

        XmlPullParser xpp = Xml.newPullParser();
        xpp.setInput(is, "UTF-8");
        int eventType = xpp.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_DOCUMENT:
                    list = new ArrayList<ComponentInfo>();
                    break;

                case XmlPullParser.START_TAG:
                    if (xpp.getName().equals("app")) {
                        componentInfo = new ComponentInfo();
                    } else if (xpp.getName().equals("componentPackage")) {
                        eventType = xpp.next();
                        componentInfo.setComponentPackage(xpp.getText());
                    } else if (xpp.getName().equals("componentClass")) {
                        eventType = xpp.next();
                        componentInfo.setComponentClass(xpp.getText());
                    } else if (xpp.getName().equals("componentRow")) {
                        eventType = xpp.next();
                        componentInfo.setRow(xpp.getText());
                    } else if (xpp.getName().equals("componentColumn")) {
                        eventType = xpp.next();
                        componentInfo.setColumn(xpp.getText());
                    }
                    break;
                case XmlPullParser.END_TAG:
                    if (xpp.getName().equals("app")) {
                        list.add(componentInfo);
                        componentInfo = null;
                    }
                    break;
            }
            eventType = xpp.next();
        }
        return list;
    }
}
