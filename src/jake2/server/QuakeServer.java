/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
/* Modifications
   Copyright 2003-2004 Bytonic Software
   Copyright 2010 Google Inc.
*/
package jake2.server;

import static jake2.qcommon.Defines.CVAR_NOSET;

import jake2.qcommon.Cbuf;
import jake2.qcommon.Compatibility;
import jake2.qcommon.Cvar;
import jake2.qcommon.Globals;
import jake2.qcommon.Qcommon;
import jake2.qcommon.ResourceLoader;
import jake2.sound.DummyDriver;
import jake2.sound.S;
import jake2.sys.Timer;

public class QuakeServer {

//  public static final String BUILDSTRING = "Java "
//      + System.getProperty("java.version");
//  public static final String CPUSTRING = System.getProperty("os.arch");

  public static void run(String[] args) {
    Globals.dedicated = Cvar.Get("dedicated", "1", CVAR_NOSET);

    // in C the first arg is the filename
    int argc = (args == null) ? 1 : args.length + 1;
    String[] c_args = new String[argc];
    c_args[0] = "GQuake";
    if (argc > 1) {
      System.arraycopy(args, 0, c_args, 1, argc - 1);
    }

    Globals.nostdout = Cvar.Get("nostdout", "0", 0);

    Cvar.SetValue("deathmatch", 1);
    Cvar.SetValue("coop", 0);

    S.impl = new DummyDriver();
    Qcommon.Init(c_args);

    
    // Start off on map demo1.
//    Cbuf.AddText("begin\n");
//    Cbuf.Execute();

    long oldtime = Timer.Milliseconds();
    long newtime;
    long time;
    while (true) {
      // find time spending rendering last frame
      newtime = Timer.Milliseconds();
      time = newtime - oldtime;

      ResourceLoader.Pump();
      if (time > 0) {
        try {
          Qcommon.Frame((int)time);
        } catch (Throwable e) {
          Compatibility.printStackTrace(e);
        }
      }
      oldtime = newtime;
    }
  }
  
}
