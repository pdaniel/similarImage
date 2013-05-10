/*  Copyright (C) 2013  Nicholas Wright
    
    This file is part of similarImage - A similar image finder using pHash
    
    mmut is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.github.dozedoff.similarImage.hash;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amd.aparapi.Kernel;
import com.amd.aparapi.Kernel.EXECUTION_MODE;
import com.amd.aparapi.Range;
import com.github.dozedoff.commonj.time.StopWatch;

public class GpuDctTest {
	private final static Logger logger = LoggerFactory.getLogger(GpuDctTest.class);
	private final int SAMPLE_SIZE = 2000000;
	private final int LOOP_SIZE = 5000;
	
	private float[] generateFloatArray(int size){
		float result[] = new float[size];
		
		for(int i=0; i < size; i++){
			result[i] = (float)(Math.random()*10);
		}
		return result;
	}
	
	private double[] generateDoubleArray(int size){
		double result[] = new double[size];
		
		for(int i=0; i < size; i++){
			result[i] = (Math.random()*10);
		}
		return result;
	}
	
	private float[][] generateFloatGrid(int size) {
		float data[][] = new float[size][size];
		
		for(int i = 0; i < size; i++){
			for(int j = 0; j < size; j++){
				data[i][j] = (float) Math.random();
			}
		}
		
		return data;
	}
	
	private double[][] generateDoubleGrid(int size) {
		double data[][] = new double[size][size];
		
		for(int i = 0; i < size; i++){
			for(int j = 0; j < size; j++){
				data[i][j] = Math.random();
			}
		}
		
		return data;
	}
	
	private double[] initCoefficients(int size) {
		double c[] = new double[size];

		for (int i = 1; i < size; i++) {
			c[i] = 1;
		}
		c[0] = 1 / Math.sqrt(2.0);

		return c;
	}
	
	private double[] unwrap2dArray(double array[][]) {
		int size = array.length * array[0].length;
		double flatArray[] = new double[size];
		int pointer = 0;
		
		for(int i = 0; i < array.length; i++) {
			for(int j = 0; j < array[0].length; j++) {
				flatArray[pointer] = array[i][j];
				pointer++;
			}
		}
		
		return flatArray;
	}

	@Ignore
	@Test
	public void addFloatsTest() {
		final float[] floatA = generateFloatArray(SAMPLE_SIZE);
		final float[] resultGPU = new float[SAMPLE_SIZE];
		final float[] resultCPU = new float[SAMPLE_SIZE];
		
		StopWatch sw = new StopWatch();
		sw.start();
		
		for(int i=0; i < SAMPLE_SIZE; i++){
			for(int j=0; j < LOOP_SIZE; j++){
				resultCPU[i] += floatA[i];
			}
		}
		
		sw.stop();
		logger.info("CPU float add took {}", sw.getTime());
		
		sw = new StopWatch();
		
		Kernel kernel = new Kernel() {
			@Override
			public void run() {
				int i = getGlobalId();
				calc(i);
			}
			
			private void calc(int id) {
				for (int j = 0; j < LOOP_SIZE; j++) {
					resultGPU[id] += floatA[id];
				}
			}
		};
		
		Range range = Range.create(resultGPU.length);
		
		sw.start();
		kernel.execute(range);
		sw.stop();
		kernel.dispose();
		logger.info("GPU float add took {}", sw.getTime());
		
		for(int i=0; i < SAMPLE_SIZE; i++){
			assertThat(resultGPU[i], is(resultCPU[i]));
		}
	}
	
	@Test
	public void dctTest() {
		final int SIZE = 32;
		final double[] coeff = initCoefficients(SIZE);
		double data[][] = generateDoubleGrid(SIZE);
		
		final double dataFlat[] = unwrap2dArray(data);
		final double dctGpu[] = new double[SIZE * SIZE]; //result
		final double dctCpu[] = new double[SIZE * SIZE]; //result
		
		DctKernel dctKernel = new DctKernel(coeff, SIZE, dataFlat, dctGpu);
		
		Range range = Range.create2D(SIZE, SIZE);
		dctKernel.setExecutionMode(EXECUTION_MODE.GPU);
		dctKernel.execute(range);
		dctKernel.dispose();
		
		dctKernel.setup(dataFlat, dctCpu);
		
		range = Range.create2D(SIZE, SIZE);
		dctKernel.setExecutionMode(EXECUTION_MODE.CPU);
		dctKernel.execute(range);
		dctKernel.dispose();
		int runLenght = SIZE * SIZE;
		Assert.assertArrayEquals(dctCpu, dctGpu, 0);
	}
	
	@Test
	public void doubleSupportTest() {
		final double test[] = new double[SAMPLE_SIZE];
		
		Kernel kernel = new Kernel() {
			 
			@Override
			public void run() {
				int i = getGlobalId();
				test[i]++;
			}
		};
		
		kernel.setExecutionMode(EXECUTION_MODE.GPU);
		kernel.execute(SAMPLE_SIZE);
		kernel.dispose();
	}
	
	
	@Test
	public void sinDoubleTest() {
		final int SAMPLE_SIZE = 1024;
		final double data[] = generateDoubleArray(SAMPLE_SIZE);
		final double cpu[]= new double[SAMPLE_SIZE], gpu[] = new double[SAMPLE_SIZE];
		
		Kernel kernel = new SinDoubleKernel(data, gpu);
		kernel.setExecutionMode(EXECUTION_MODE.GPU);
		kernel.execute(SAMPLE_SIZE);
		kernel.dispose();
		
		kernel = new SinDoubleKernel(data, cpu);
		kernel.setExecutionMode(EXECUTION_MODE.CPU);
		kernel.execute(SAMPLE_SIZE);
		kernel.dispose();
		
		Assert.assertArrayEquals(cpu, gpu, 0);
	}
	
	@Test
	public void sinFloatTest() {
		final int SAMPLE_SIZE = 1024;
		final float data[] = generateFloatArray(SAMPLE_SIZE);
		final float cpu[]= new float[SAMPLE_SIZE], gpu[] = new float[SAMPLE_SIZE];
		
		Kernel kernel = new SinFloatKernel(data, gpu);
		kernel.setExecutionMode(EXECUTION_MODE.GPU);
		kernel.execute(SAMPLE_SIZE);
		kernel.dispose();
		
		kernel = new SinFloatKernel(data, cpu);
		kernel.setExecutionMode(EXECUTION_MODE.CPU);
		kernel.execute(SAMPLE_SIZE);
		kernel.dispose();
		
		Assert.assertArrayEquals(cpu, gpu, 0);
	}
	
	class SinDoubleKernel extends Kernel {
		final double data[], result[];
		
		public SinDoubleKernel(double[] data, double[] result) {
			this.data = data;
			this.result = result;
		}

		@Override
		public void run() {
			int i = getGlobalId();
			result[i] = sin(data[i]);
		}
	}
	
	class SinFloatKernel extends Kernel {
		final float data[], result[];
		
		public SinFloatKernel(float[] data, float[] result) {
			this.data = data;
			this.result = result;
		}

		@Override
		public void run() {
			int i = getGlobalId();
			result[i] = sin(data[i]);
		}
	}
}
