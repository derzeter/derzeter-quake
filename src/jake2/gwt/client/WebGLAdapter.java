/*
Copyright (C) 2010 Copyright 2010 Google Inc.

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
package jake2.gwt.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayInteger;
import com.google.gwt.corp.gfx.client.canvas.CanvasElement;
import com.google.gwt.corp.webgl.client.ByteBufferWrapper;
import com.google.gwt.corp.webgl.client.HasWebGLArray;
import com.google.gwt.corp.webgl.client.WebGL;
import com.google.gwt.corp.webgl.client.WebGLArray;
import com.google.gwt.corp.webgl.client.WebGLByteArray;
import com.google.gwt.corp.webgl.client.WebGLFloatArray;
import com.google.gwt.corp.webgl.client.WebGLIntArray;
import com.google.gwt.corp.webgl.client.WebGLShortArray;
import com.google.gwt.corp.webgl.client.WebGLUnsignedByteArray;
import com.google.gwt.corp.webgl.client.WebGLUnsignedShortArray;
import com.google.gwt.corp.webgl.client.WebGL.Texture;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;

import jake2.render.DisplayMode;
import jake2.render.GLAdapter;
import jake2.render.gl.AbstractGL20Adapter;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Partial mapping of lwjgl to WebGL.
 * 
 * @author Stefan Haustein
 */
public class WebGLAdapter extends AbstractGL20Adapter {

  static final int SMALL_BUF_COUNT = 4;
  
  int uMvpMatrix;
  int uSampler0;
  int uSampler1;
  int uTexEnv0;
  int uTexEnv1;
  int uEnableTexture0;
  int uEnableTexture1;

  JsArray<WebGL.Buffer> staticBuffers = (JsArray<WebGL.Buffer>) JavaScriptObject.createArray();
  
  class BufferData {
	  WebGLArray<?> toBind;
	  WebGL.Buffer buffer;
	  int byteStride;
	  int size;
	  int type;
	  int byteSize;
	  boolean normalize;
  }
  
  private BufferData[] bufferData = new BufferData[SMALL_BUF_COUNT];
  
  public final WebGL gl;

  int logCount = 0;
  
  public void log(String msg) {
  	if (logCount >= 1000) {
  		return;
  	}
	log_((logCount++) + ": " + msg);
   };

  private native void log_(String msg) /*-{
	console.log(msg);
}-*/;

  FloatBuffer colorBuffer;
  private CanvasElement canvas;
  
  JsArray<WebGL.Texture> textures = (JsArray<Texture>) JsArray.createArray();
  JsArrayInteger textureFormats = (JsArrayInteger) JsArray.createArray();

  private int clientActiveTexture = 0;
  private int activeTexture = 0;
  private int[] boundTextureId = new int[2];
  private int[] texEnvMode = new int[2];
  private JsArrayInteger textureFormat = (JsArrayInteger) JavaScriptObject.createArray();
  private WebGL.Buffer elementBuffer;

  
  public WebGLAdapter(CanvasElement canvas) {
    super(canvas.getWidth(), canvas.getHeight());

    this.canvas = canvas;
    gl = WebGL.getContext(canvas, JavaScriptObject.createObject());

    if (gl == null) {
      throw new UnsupportedOperationException("WebGL N/A");
    }
    
    initShader();
    checkError("initShader");
    
    elementBuffer = gl.glCreateBuffer();
    checkError("createBuffer f. elements");
    
    for (int i = 0; i < bufferData.length; i++) {
		  BufferData bd = new BufferData();
		  bd.buffer = gl.glCreateBuffer();
		    checkError("createBuffer" + i);
		  bufferData[i] = bd;
	  }

    
  }

  
  
//  private native JavaScriptObject noPremultipliedAlpha() /*-{
//	return {premultipliedAlpha: false};
//  }-*/;

private WebGL.Shader loadShader(int shaderType, String shaderSource) {
    // Create the shader object
    WebGL.Shader shader = gl.glCreateShader(shaderType);
    if (shader == null) {
      throw new RuntimeException();
    }
    // Load the shader source
    gl.glShaderSource(shader, shaderSource);

    // Compile the shader
    gl.glCompileShader(shader);

    // Check the compile status
    boolean compiled = gl.glGetShaderParameterb(shader, WebGL.GL_COMPILE_STATUS);
    if (!compiled) {
      // Something went wrong during compilation; get the error
      throw new RuntimeException ("Shader compile error: " + gl.glGetShaderInfoLog(shader));
    }
    return shader;
  }

  public static native String getUserAgent() /*-{
    return navigator.userAgent.toLowerCase();
  }-*/;
  
  private void initShader() {
    String vertexShaderSource =
      "attribute vec4 a_position;\n" + 
      "attribute vec4 a_color;\n" + 
      "attribute vec2 a_texCoord0; \n" +
      "attribute vec2 a_texCoord1; \n" +
      "uniform mat4 u_mvpMatrix; \n" + 
      "varying vec4 v_color; \n" + 
      "varying vec2 v_texCoord0; \n" + 
      "varying vec2 v_texCoord1; \n" + 
      "void main() {\n" + 
      "  gl_Position = u_mvpMatrix * a_position;\n" + 
      "  v_color = a_color;        \n" +
      "  v_texCoord0 = a_texCoord0;  \n" +
      "  v_texCoord1 = a_texCoord1;  \n" +
      "}\n";

    String fragmentShaderSource =
        "uniform sampler2D s_texture0;  \n" + 
        "uniform sampler2D s_texture1;  \n" + 
        "uniform int s_texEnv0;  \n" + 
        "uniform int s_texEnv1;  \n" + 
        "uniform int u_enable_texture_0; \n" +
        "uniform int u_enable_texture_1; \n" +
        "varying vec4 v_color; \n" + 
        "varying vec2 v_texCoord0;      \n" + 
        "varying vec2 v_texCoord1;" +
        "vec4 finalColor;      \n" + 
        "void main() {                 \n" + 
        "finalColor = v_color;" + 
        "  if (u_enable_texture_0 == 1) { \n" +
        "    vec4 texel = texture2D(s_texture0, v_texCoord0); \n" +
        "    if(s_texEnv0 == 1) { "+
        "      finalColor = finalColor * texel;"+
        "    } else if (s_texEnv0 == 2) {" +
        "      finalColor = vec4(texel.r, texel.g, texel.b, finalColor.a);" +
        "    } else {"+
        "      finalColor = texel;"+
        "    }"+
        "}"+
        " if (u_enable_texture_1 == 1) { \n" +
        "      vec4 texel = texture2D(s_texture1, v_texCoord1); \n" +
        "    if(s_texEnv1 == 1) { "+
        "      finalColor = finalColor * texel;"+
        "    } else if (s_texEnv1 == 2) {" +
        "      finalColor = vec4(texel.r, texel.g, texel.b, finalColor.a);" +
        "    } else {"+
        "      finalColor = texel;"+
        "    }"+
        "  } \n" +
        // simple alpha check
        " if (finalColor.a == 0.0) {\n" +
        "   discard;\n" +
        " }\n" +
        " gl_FragColor = finalColor; \n" +
        "}\n";
//    }
    
    // create our shaders
    WebGL.Shader vertexShader = loadShader(WebGL.GL_VERTEX_SHADER, vertexShaderSource);
    WebGL.Shader fragmentShader = loadShader(WebGL.GL_FRAGMENT_SHADER, fragmentShaderSource);
    
    if (vertexShader == null || fragmentShader == null) {
    	log("Shader error");
      throw new RuntimeException("shader error");
    }

    // Create the program object
    WebGL.Program programObject = gl.glCreateProgram();
    if (programObject == null || gl.glGetError() != WebGL.GL_NO_ERROR) {
    	log("Program errror");
      throw new RuntimeException("program error");
    }
    // Attach our two shaders to the program

    gl.glAttachShader(programObject, vertexShader);
    gl.glAttachShader(programObject, fragmentShader);

    // Bind "vPosition" to attribute 0
    gl.glBindAttribLocation(programObject, GLAdapter.ARRAY_POSITION, "a_position");
    gl.glBindAttribLocation(programObject, GLAdapter.ARRAY_COLOR, "a_color");
    gl.glBindAttribLocation(programObject, GLAdapter.ARRAY_TEXCOORD_0, "a_texCoord0");
    gl.glBindAttribLocation(programObject, GLAdapter.ARRAY_TEXCOORD_1, "a_texCoord1");

    // Link the program
    gl.glLinkProgram(programObject);

    //TODO(haustein) get position, color from the linker, too
    uMvpMatrix = gl.glGetUniformLocation(programObject, "u_mvpMatrix");
    uSampler0 = gl.glGetUniformLocation(programObject, "s_texture0");
    uSampler1 = gl.glGetUniformLocation(programObject, "s_texture1");
    uTexEnv0 = gl.glGetUniformLocation(programObject, "s_texEnv0");
    uTexEnv1 = gl.glGetUniformLocation(programObject, "s_texEnv1");

    uEnableTexture0 = gl.glGetUniformLocation(programObject, "u_enable_texture_0");
    uEnableTexture1 = gl.glGetUniformLocation(programObject, "u_enable_texture_1");

    // // Check the link status
    boolean linked = gl.glGetProgramParameterb(programObject, WebGL.GL_LINK_STATUS);
    if (!linked) {
      throw new RuntimeException("linker Error: " + gl.glGetProgramInfoLog (programObject));
    }

    gl.glUseProgram(programObject);

    gl.glUniform1i(uSampler0, 0);
    gl.glUniform1i(uSampler1, 1);
    gl.glActiveTexture(GL_TEXTURE0);
  }

  public String webGLFloatArrayToString(WebGLFloatArray fa) {
	  StringBuilder sb = new StringBuilder();
	  sb.append("len: " + fa.getLength());
	  sb.append("data: ");
	  for (int i = 0; i < Math.min(fa.getLength(), 10); i++) {
		  sb.append(fa.get(i) + ",");
	  }
	  return sb.toString();
  }

  public String webGLIntArrayToString(WebGLIntArray fa) {
	  StringBuilder sb = new StringBuilder();
	  sb.append("len: " + fa.getLength());
	  sb.append("data: ");
	  for (int i = 0; i < Math.min(fa.getLength(), 10); i++) {
		  sb.append(fa.get(i) + ",");
	  }
	  return sb.toString();
  }

  public String webGLUnsignedShortArrayToString(WebGLUnsignedShortArray fa) {
	  StringBuilder sb = new StringBuilder();
	  sb.append("len: " + fa.getLength());
	  sb.append("data: ");
	  for (int i = 0; i < Math.min(fa.getLength(), 10); i++) {
		  sb.append(fa.get(i) + ",");
	  }
	  return sb.toString();
  }

  @Override
  public void glActiveTexture(int texture) {
  	gl.glActiveTexture(texture);
  	activeTexture = texture - GL_TEXTURE0;
  	checkError("glActiveTexture");
  }

  @Override
  public void glAlphaFunc(int i, float j) {
  	// TODO Auto-generated method stub
  }

  @Override
  public void glClientActiveTexture(int texture) {
	  clientActiveTexture = texture - GL_TEXTURE0;
  }

  @Override
  public void glColorPointer(int size, int stride, FloatBuffer colorArrayBuf) {
	  glColorPointer(size, WebGL.GL_FLOAT, stride, colorArrayBuf);
  }

  @Override
  public void glColorPointer(int size, boolean unsigned, int stride, ByteBuffer colorAsByteBuffer) {
  	glColorPointer(size, unsigned ? WebGL.GL_UNSIGNED_BYTE : WebGL.GL_BYTE, stride, colorAsByteBuffer);
  }
  
  private final void glColorPointer(int size, int type, int stride, Buffer buf) {
    glVertexAttribPointer(GLAdapter.ARRAY_COLOR, size, type, true, stride, buf);
    checkError("glColorPointer");
  }

  @Override
  public void glDeleteTextures(IntBuffer texnumBuffer) {
	  for (int i = 0; i < texnumBuffer.remaining(); i++) {
		  int tid = texnumBuffer.get(texnumBuffer.position() + i);
	      gl.glDeleteTexture(textures.get(tid));
	      textures.set(tid, null);
	      checkError("glDeleteTexture");
	  }
  }
  
  
  @Override
  public void glDepthFunc(int func) {
  	gl.glDepthFunc(func);
  	checkError("glDepthFunc");
  }

  @Override
  public void glDepthMask(boolean b) {
	  gl.glDepthMask(b);
	  	checkError("glDepthMask");
  }

  @Override
  public void glDepthRange(float gldepthmin, float gldepthmax) {
	  gl.glDepthRange(gldepthmin, gldepthmax);
	  	checkError("glDepthRange");
  	
  }

  @Override
  public void glDrawBuffer(int buf) {
  	// specify which color buffers are to be drawn into
  	
  }

  @Override
  public void glDrawElements(int mode, ShortBuffer srcIndexBuf) {
	  prepareDraw();
	  
	  gl.glBindBuffer(WebGL.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
	  checkError("bindBuffer(el)");
	  gl.glBufferData(WebGL.GL_ELEMENT_ARRAY_BUFFER, 
			  getWebGLArray(srcIndexBuf, WebGL.GL_UNSIGNED_SHORT), 
			  WebGL.GL_DYNAMIC_DRAW);
	  checkError("bufferData(el)");

	  int count = srcIndexBuf.remaining();
	  gl.glDrawElements(mode, count, WebGL.GL_UNSIGNED_SHORT, 0);
	  checkError("drawElements");
  }


  
  @Override
  public void glFinish() {
  	gl.glFinish();
  }

  @Override
  public String glGetString(int id) {
  	return gl.glGetString(id);
  }
  


  
//  @Override
//  public final void glHint(int h, int i) {
//    gl.glHint(h, i);
//  }


  @Override
  public void glPixelStorei(int i, int j) {
  	gl.glPixelStorei(i, j);
  }

  @Override
  public void glPointParameterf(int id, float value) {
  	// TODO Auto-generated method stub
  	
  }

  @Override
  public void glPointSize(float value) {
  	// TODO Auto-generated method stub
  	
  }

  @Override
  public void glPolygonMode(int i, int j) {
  	// TODO Auto-generated method stub
  	
  }

  @Override
  public void glReadPixels(int x, int y, int width, int height, int glBgr,
  		int glUnsignedByte, ByteBuffer image) {
  	// TODO Auto-generated method stub
  	
  }

  @Override
  public void glTexCoordPointer(int size, int byteStride, FloatBuffer buf) {
	  glVertexAttribPointer(GLAdapter.ARRAY_TEXCOORD_0 + clientActiveTexture, size, 
			  GL_FLOAT, false, byteStride, buf);
	  checkError("texCoordPointer");
  }

  
  @Override
  public void glTexEnvi(int target, int pid, int value) {
 		texEnvMode[activeTexture] = value;
 }

  @Override
  public void glTexImage2D(int target, int level, int internalformat, int width,
  		int height, int border, int format, int type, ByteBuffer pixels) {
	  
	textureFormat.set(boundTextureId[activeTexture], internalformat);
	WebGLArray<?> array = getWebGLArray(pixels, type);
  	gl.glTexImage2D(target, level, internalformat, width, height, border, format, type, array);
  	checkError("glTexImage2D");
  }

  @Override
  public void glTexImage2D(int target, int level, int internalformat, int width,
  		int height, int border, int format, int type, IntBuffer pixels) {
	  
	textureFormat.set(boundTextureId[activeTexture], internalformat);
	WebGLArray<?> array = getWebGLArray(pixels, type);
	gl.glTexImage2D(target, level, internalformat, width, height, border, format, type, array);
	checkError("glTexImage2D");
  }

  @Override
  public void glTexParameteri(int glTexture2d, int glTextureMinFilter,
  		int glFilterMin) {
  	gl.glTexParameteri(glTexture2d, glTextureMinFilter, glFilterMin);
  	checkError("glTexParameteri");
  }

  @Override
  public void glTexSubImage2D(int target, int level, int xoffset, int yoffset,
  		int width, int height, int format, int type, ByteBuffer pixels) {
	  WebGLArray<?> array = getWebGLArray(pixels, type);
	  gl.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, array);
	  checkError("glTexSubImage2D");
  }

  @Override
  public void glTexSubImage2D(int target, int level, int xoffset, int yoffset,
  		int width, int height, int format, int type, IntBuffer pixels) {
	  WebGLArray<?> array = getWebGLArray(pixels, type);
	  gl.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, array);
	  checkError("glTexSubImage2D");
  }

  @Override
  public void glVertexPointer(int size, int byteStride, FloatBuffer buf) {
	    glVertexAttribPointer(GLAdapter.ARRAY_POSITION, size, 
	    		GL_FLOAT, false, byteStride, buf);
	    checkError("glVertexPointer");
  	
  }
  
  @Override
  public void setDisplayMode(DisplayMode displayMode) {
  	canvas.setWidth(displayMode.width);
  	canvas.setHeight(displayMode.height);
  }

  public DisplayMode[] getAvailableDisplayModes() {
		return new DisplayMode[] {getDisplayMode(), 
				new DisplayMode(Window.getClientWidth(), Window.getClientHeight(), 32, 60)};
	}
  
  
  @Override
  public void shutdow() {
  	// TODO Auto-generated method stub
  	
  }

  @Override
  public void swapBuffers() {
  	// TODO Auto-generated method stub
  	
  }  
  @Override
  public final void glBindTexture(int target, int textureId) {
    Texture texture = textures.get(textureId);
    if (texture == null) {
      texture = gl.glCreateTexture();
      textures.set(textureId, texture);
    }
	  
//    log ("binding texture " + texture + " id " + textureId + " for activeTexture: " + (activeTexture-GL_TEXTURE0));
    gl.glBindTexture(target, texture);
    checkError("glBindTexture");
    
    boundTextureId[activeTexture] = textureId;
    
   // glColor3f((float)Math.random(), (float)Math.random(), (float)Math.random());
  }

  @Override
  public final void glBlendFunc(int a, int b) {
    gl.glBlendFunc(a, b);
  	checkError("glBlendFunc");

  }

  @Override
  public final void glClear(int mask) {
    gl.glClear(mask);  	
    checkError("glClear");

  }


  @Override
  public final void glColor4f(float red, float green, float blue, float alpha) {
    gl.glVertexAttrib4f(GLAdapter.ARRAY_COLOR, red, green, blue, alpha);
    checkError("glColor4f");
  }

  public void glTexImage2d(int target, int level, Element image) {
//		log("setting texImage2d; image: " + image.getSrc());

	  gl.glTexImage2D(target, level, image);
	  checkError("texImage2D");
  }

  @Override
  public final void glEnable(int i) {
    if (i == GL_TEXTURE_2D) {
    	switch (activeTexture) {
    	case 0:
    		gl.glUniform1i(uEnableTexture0, 1);
    		break;
    	case 1:
    		gl.glUniform1i(uEnableTexture1, 1);
    		break;
    	default:
    		throw new RuntimeException();
    	}
    }
     gl.glEnable(i);
     checkError("glEnable");
  }

  @Override
  public final int glGetError() {
    return gl.glGetError();
  }

  @Override
  public final void glClearColor(float f, float g, float h, float i) {
    gl.glClearColor(f, g, h, i);
    checkError("glClearColor");
  }

  @Override
  public void glDrawArrays(int mode, int first, int count) {
    prepareDraw();
//    log("drawArrays mode:" + mode + " first:" + first + " count:" +count);
    gl.glDrawArrays(mode, first, count);
    checkError("drawArrays");
  }


  public void checkError(String string) {
//	  int err = gl.glGetError();
//	if (err != GL_NO_ERROR) {
//		log("GL_ERROR in " + string + "(): " + err);
//		//throw new RuntimeException("GL_ERROR in " + string + "(): " + err);
//	}
  }
  
  
  public void updatTCBuffer(FloatBuffer buf, int offset, int count) {
	  BufferData bd = bufferData[GLAdapter.ARRAY_TEXCOORD_0];
	  gl.glBindBuffer(WebGL.GL_ARRAY_BUFFER, bd.buffer);
	  
	  int pos = buf.position();
	  int limit = buf.limit();
	  
	  buf.position(pos + offset);
	  buf.limit(pos + offset + count);
	  
	  WebGLArray<?> data = getWebGLArray(buf, GL_FLOAT);
	  
	  gl.glBufferSubData(WebGL.GL_ARRAY_BUFFER, offset * 4, data);
	  
	  buf.position(pos);
	  buf.limit(limit);
  }		

  private void prepareDraw() {
    if (updateMvpMatrix()) {
      gl.glUniformMatrix4fv(uMvpMatrix, false, WebGLFloatArray.create(mvpMatrix));
      checkError("prepareDraw");
    }
    
    gl.glUniform1i(uTexEnv0, getTextureMode(0));
    gl.glUniform1i(uTexEnv1, getTextureMode(1));

//    StringBuilder sizes = new StringBuilder();
    
    for (int i = 0; i < SMALL_BUF_COUNT; i++) {
    	BufferData bd = bufferData[i];
    	if(bd.toBind != null) {
  	          gl.glBindBuffer(WebGL.GL_ARRAY_BUFFER, bd.buffer);
  	          checkError("bindBuffer"+ i);
  	          
//  	          int len = bd.toBind.getByteLength();
//  	          if (len < bd.byteSize) {
//  	        	  gl.glBufferSubData(WebGL.GL_ARRAY_BUFFER, 0, bd.toBind);
//  	          } else {
//  	        	  bd.byteSize = len;
  	        	  gl.glBufferData(WebGL.GL_ARRAY_BUFFER, bd.toBind, 
    	    		  WebGL.GL_STREAM_DRAW); 
//  	          }
  	          checkError("bufferData" + i);

    	      gl.glVertexAttribPointer(i, bd.size, bd.type, bd.normalize, bd.byteStride, 0);
  	          checkError("vertexAttribPointer");
  	          
  	          bd.toBind = null;
    	} 
    }
    
//    log ("prepDraw: " + sizes);
  }



  private int getTextureMode(int i) {
	return texEnvMode[i] == GL_REPLACE ? 0 : 
		(textureFormats.get(boundTextureId[i]) == 3 ? 2 : 1);
}


  @Override
  public final void glScissor(int i, int j, int width, int height) {
    gl.glScissor(i, j, width, height);
    checkError("glScissor");
  }

  @Override
  public void glTexParameterf(int target, int pname, float param) {
    gl.glTexParameterf(target, pname, param);
    checkError("glTexParameterf");
  }



  @Override
  public final void glEnableClientState(int i) {
    switch(i) {
      case GL_COLOR_ARRAY:
        gl.glEnableVertexAttribArray(GLAdapter.ARRAY_COLOR);
	    checkError("enableClientState colorArr");
        break;
      case GL_VERTEX_ARRAY:
        gl.glEnableVertexAttribArray(GLAdapter.ARRAY_POSITION);
	    checkError("enableClientState vertexArrr");
        break;
      case GL_TEXTURE_COORD_ARRAY:
    	switch (clientActiveTexture) {
    	case 0:
    		gl.glEnableVertexAttribArray(GLAdapter.ARRAY_TEXCOORD_0);
    	    checkError("enableClientState texCoord0");
    		break;    		
    	case 1:
    		gl.glEnableVertexAttribArray(GLAdapter.ARRAY_TEXCOORD_1);
    	    checkError("enableClientState texCoord1");
    		break;
    	default:
    		throw new RuntimeException();
    	}
        break;
      default:
        log("unsupported / unrecogized client state "+ i);
    }
  }


  @Override
  public final void glDisableClientState(int i) {
    switch(i) {
      case GL_COLOR_ARRAY:
        gl.glDisableVertexAttribArray(GLAdapter.ARRAY_COLOR);
        break;
      case GL_VERTEX_ARRAY:
        gl.glDisableVertexAttribArray(GLAdapter.ARRAY_POSITION);
        break;
      case GL_TEXTURE_COORD_ARRAY:
      	switch (clientActiveTexture) {
    	case 0:
    		gl.glDisableVertexAttribArray(GLAdapter.ARRAY_TEXCOORD_0);
    		break;
    	case 1:
    		gl.glDisableVertexAttribArray(GLAdapter.ARRAY_TEXCOORD_1);
    		break;
    	default:
    		throw new RuntimeException();
    	}
        break;
      default:
        log("unsupported / unrecogized client state");
    }
    checkError("DisableClientState");
  }


  @Override
  public final void glDisable(int i) {
    if (i == GL_TEXTURE_2D) {
    	switch (activeTexture) {
    	case 0:
    		gl.glUniform1i(uEnableTexture0, 0);
    		break;
       	case 1:
    		gl.glUniform1i(uEnableTexture1, 0);
    		break;
    	default:
    		throw new RuntimeException();
    	}
    } 
    gl.glDisable(i);
    checkError("glDisable");
  }

  @Override
  public final void glCullFace(int c) {
    gl.glCullFace(c);
    checkError("glCullFace");
  }



  @Override
  public final void glShadeModel(int s) {
  }


  @Override
  public final void glViewport(int x, int y, int w, int h) {
	  super.glViewport(x, y, w, h);
    gl.glViewport(x, y, w, h);
    checkError("glViewport");
  }
  
  public void glVertexAttribPointer(int arrayId, int size, 
		  int type, boolean normalize, int byteStride, Buffer nioBuffer) {
	  BufferData bd= bufferData[arrayId];
	  bd.byteStride = byteStride;
	  bd.size = size;
	  bd.normalize = normalize;
	  bd.type = type;
      WebGLArray<?> webGLArray = getWebGLArray(nioBuffer, type);
	  bd.toBind = webGLArray;		  
  }
  
  public void glVertexAttribPointer(int arrayId, int size, 
		  int type, boolean normalize, int byteStride, int offset, Buffer nioBuffer,
		  int staticDrawId) {
	  WebGL.Buffer buffer = staticBuffers.get(staticDrawId);
	  if (buffer == null) {
		  buffer = gl.glCreateBuffer();
		  staticBuffers.set(staticDrawId, buffer);
		  gl.glBindBuffer(WebGL.GL_ARRAY_BUFFER, buffer);
		  WebGLArray<?> webGLArray = getWebGLArray(nioBuffer, type);
		  gl.glBufferData(WebGL.GL_ARRAY_BUFFER, webGLArray, WebGL.GL_STATIC_DRAW); 
		  checkError("bufferData");
		  log("static buffer created; id: " + staticDrawId + " remaining: " + nioBuffer.remaining());
	  } 
	  gl.glBindBuffer(WebGL.GL_ARRAY_BUFFER, buffer);
	  gl.glVertexAttribPointer(arrayId, size, type, normalize, byteStride, offset);
	  bufferData[arrayId].toBind = null;
	  checkError("vertexAttribPointer");	
  }


  private WebGLArray<?> getWebGLArray(Buffer buffer, int type) {

	  int elementSize;
	  HasWebGLArray arrayHolder;
	  
	  if (!(buffer instanceof HasWebGLArray)) {
		  if (type != GL_BYTE && type != GL_UNSIGNED_BYTE) {
			  log("buffer byte order problem");
			  throw new RuntimeException("Buffer byte order problem");
		  }
		  if (buffer instanceof IntBuffer) {
			  elementSize = 4;
		  } else {
			  throw new RuntimeException("NYI");
		  }
		  arrayHolder = (HasWebGLArray) ((ByteBufferWrapper) buffer).getByteBuffer();
	  } else {
		  arrayHolder = (HasWebGLArray) buffer;
		  elementSize = arrayHolder.getElementSize();
	  }
	  
	  WebGLArray<?> webGLArray = arrayHolder.getWebGLArray();
	  int remainingBytes = buffer.remaining() * elementSize;

	  int byteOffset = webGLArray.getByteOffset() + buffer.position() * elementSize;
	  
	  switch (type) {
	  case WebGL.GL_FLOAT: 
		  return WebGLFloatArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes / 4);
		  
	  case WebGL.GL_UNSIGNED_BYTE: 
		  return WebGLUnsignedByteArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes);
		  
	  case WebGL.GL_UNSIGNED_SHORT: 
		  return WebGLUnsignedShortArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes / 2);

	  case WebGL.GL_INT: 
		  return WebGLIntArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes / 4);

	  case WebGL.GL_SHORT: 
		  return WebGLShortArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes / 2);
  
	  case WebGL.GL_BYTE: 
		  return WebGLByteArray.create(webGLArray.getBuffer(), byteOffset, remainingBytes);
	  }
	  
	  throw new IllegalArgumentException();
  }

public void glGenerateMipmap(int t) {
	gl.glGenerateMipmap(t);
	checkError("genMipmap");
}

  
}

