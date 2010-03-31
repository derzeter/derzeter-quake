// Copyright (C) 2009 Google Inc. All Rights Reserved.
package jake2.render.gl;


import jake2.render.DisplayMode;
import jake2.render.GLAdapter;
import jake2.render.GLDebugWrapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;


/**
 * Abstract adapter implementing some of the OpenGL ES 1.x matrix operations.
 * 
 * @author haustein@google.com (Stefan Haustein)
 */
public abstract class AbstractGL20Adapter extends GLAdapter {

  // looks like the buffer access semantics may be different for lwjgl and JSR239? :-/
  protected static final boolean AUTO_REWIND = true;

  private int matrixMode = GL_MODELVIEW;
  
  protected int viewportX;
  protected int viewportY;
  protected int viewportW;
  protected int viewportH;
  
  protected float[] projectionMatrix = new float[16];
  protected float[] modelViewMatrix = new float[16];
  private float[] textureMatrix = new float[16];

  private ArrayList<float[]> projectionMatrixStack = new ArrayList<float[]>();
  private ArrayList<float[]> modelViewMatrixStack = new ArrayList<float[]>();
  private ArrayList<float[]> textureMatrixStack = new ArrayList<float[]>();

  private float[] currentMatrix = modelViewMatrix;
  private ArrayList<float[]> currentMatrixStack = modelViewMatrixStack;

  float[] tmpMatrix = new float[16];
  protected float[] mvpMatrix = new float[16];
  private boolean mvpDirty = true;
       
  private int width;
  private int height;
  
  public AbstractGL20Adapter(int w, int h) {
    Matrix.setIdentityM(modelViewMatrix, 0);
    Matrix.setIdentityM(projectionMatrix, 0);
    Matrix.setIdentityM(textureMatrix, 0);
    this.width = w;
    this.height = h;
  }
    
  public final void glLoadIdentity() {
    Matrix.setIdentityM(currentMatrix, 0);
    mvpDirty = true;
  }

  public final void glMatrixMode(int mm) {
    switch (mm) {
      case GL_MODELVIEW:
        currentMatrix = modelViewMatrix;
        currentMatrixStack = modelViewMatrixStack;
        break;
      case GL_PROJECTION:
        currentMatrix = projectionMatrix;
        currentMatrixStack = projectionMatrixStack;
        break;
      case GL_TEXTURE:
        currentMatrix = textureMatrix;
        currentMatrixStack = textureMatrixStack;
        break;
      default:
        throw new IllegalArgumentException("Unrecoginzed matrix mode: " + mm);
    }
    this.matrixMode = mm;
  }

  public void glGetInteger(int what, IntBuffer params) {
	  switch(what) {
	  case GL_MATRIX_MODE: 
		  params.put(matrixMode);
		  break;
	  default:
		  throw new IllegalArgumentException();
	  }
  }
  
  public final void glMultMatrixf(float[] matrix, int ofs) {
    Matrix.multiplyMM(tmpMatrix, 0, currentMatrix, 0, matrix, ofs);
    System.arraycopy(tmpMatrix, 0, currentMatrix, 0, 16);
    mvpDirty = true;
  }

  public final void glPushMatrix() {
    float[] copy = new float[16];
    System.arraycopy(currentMatrix, 0, copy, 0, 16);
    currentMatrixStack.add(copy);
  }

  public final void glPopMatrix() {
    float[] top = currentMatrixStack.remove(currentMatrixStack.size() - 1);
    System.arraycopy(top, 0, currentMatrix, 0, 16);
    mvpDirty = true;
  }

  public final void glRotatef(float angle, float x, float y, float z) {
	if (x != 0 || y != 0 || z != 0) {
		// right thing to do? or rotate around a default axis?
	    Matrix.rotateM(currentMatrix, 0, angle, x, y, z);
	}
    mvpDirty = true;
  }

  public final void glScalef(float x, float y, float z) {
    Matrix.scaleM(currentMatrix, 0, x, y, z);
    mvpDirty = true;
  }

  public final void glTranslatef(float tx, float ty, float tz) {
    Matrix.translateM(currentMatrix, 0, tx, ty, tz);
    mvpDirty = true;
  }

  @Override
	public void glViewport(int x, int y, int w, int h) {
		viewportX = x;
		viewportY = y;
		viewportW = w;
		viewportH = h;
	}

  
  @Override
  public void glFrustum(double left, double right, double bottom, double top, 
      double znear, double zfar) {
    float[] matrix = new float[16];
    double temp, temp2, temp3, temp4;
    temp = 2 * znear;
    temp2 = right - left;
    temp3 = top - bottom;
    temp4 = zfar - znear;
    matrix[0] = (float) (temp / temp2);
    matrix[1] = 0;
    matrix[2] = 0;
    matrix[3] = 0;
    matrix[4] = 0;
    matrix[5] = (float) (temp / temp3);
    matrix[6] = 0;
    matrix[7] = 0;
    matrix[8] = (float) ((right + left) / temp2);
    matrix[9] = (float) ((top + bottom) / temp3);
    matrix[10] = (float) ((-zfar - znear) / temp4);
    matrix[11] = -1;
    matrix[12] = 0;
    matrix[13] = 0;
    matrix[14] = (float) ((-temp * zfar) / temp4);
    matrix[15] = 0;
    
    glMultMatrixf(matrix, 0);
  }

  protected boolean updateMvpMatrix() {
    if (!mvpDirty) {
      return false;
    }
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
    mvpDirty = false;
    return true;
  }
  
  @Override
  public FloatBuffer createFloatBuffer(int size) {
//	  return FloatBuffer.allocate(size);
	  return createByteBuffer(size * 4).asFloatBuffer();
  }

  @Override
  public ShortBuffer createShortBuffer(int size) {
  //	return ShortBuffer.allocate(size);
	  return createByteBuffer(size * 2).asShortBuffer();
  }
  
  @Override
  public void glOrtho(int left, int right, int bottom, int top, int near, int far) {
	  float l = left;
	  float r = right;
	  float b = bottom;
	  float n = near;
	  float f = far;
	  float t = top;
	  
	  float[] matrix = {
		2f/(r-l),
		0, 
		0,
		0,
		
		0, 
		2f/(t-b),
		0,
		0,
		
		0,
		0,
		-2f/f-n,
		0,
		
		-(r+l)/(r-l),
		-(t+b)/(t-b),
		-(f+n)/(f-n),
		1f
	  };
	  
	  glMultMatrixf(matrix, 0);
	  mvpDirty = true;
  }

  
  	public boolean project(float objX, float objY, float objZ,
          int[] view, float[] win) {
      float[] v = {objX, objY, objZ, 1f};

      float[] v2 = new float[4];

      Matrix.multiplyMV(v2, 0, mvpMatrix, 0, v, 0);

      float w = v2[3];
      if (w == 0.0f) {
          return false;
      }

      float rw = 1.0f / w;

      win[0] = viewportX + viewportW * (v2[0] * rw + 1.0f) * 0.5f;
      win[1] = viewportY + viewportH * (v2[1] * rw + 1.0f) * 0.5f;
      win[2] = (v2[2] * rw + 1.0f) * 0.5f;

      return true;
  }

  

  @Override
  public void glGetFloat(int name, FloatBuffer result) {
	  switch (name) {
	  case GL_MODELVIEW:
	  case _GL_MODELVIEW_MATRIX:
//		  if (AUTO_REWIND) {
//			  result.rewind();
//		  }
		  int p = result.position();
		  result.put(modelViewMatrix);
		  result.position(p);
		  break;
	default:
		throw new IllegalArgumentException("glGetFloat("+GLDebugWrapper.c(name)+")");
	  }

  }

	@Override
	public void glLoadMatrix(FloatBuffer m) {
//		if (AUTO_REWIND) {
//				m.rewind();
//		}
		int p = m.position();
		m.get(currentMatrix);
		m.position(p);
		mvpDirty = true;
	}


	@Override
	public ByteBuffer createByteBuffer(int size) {
		ByteBuffer bb =  ByteBuffer.allocateDirect(size);
		bb.order(ByteOrder.nativeOrder());
		return bb;
	}

	@Override
	public IntBuffer createIntBuffer(int size) {
//		return IntBuffer.allocate(size);
		return createByteBuffer(size * 4).asIntBuffer();
	}
	

	@Override
	public DisplayMode getDisplayMode() {
		return new DisplayMode(width, height, 24, 60);
	}
}
