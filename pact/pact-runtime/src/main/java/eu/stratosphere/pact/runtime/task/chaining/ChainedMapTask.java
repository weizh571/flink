/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.pact.runtime.task.chaining;

import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.template.AbstractInvokable;
import eu.stratosphere.pact.common.stubs.Collector;
import eu.stratosphere.pact.common.stubs.MapStub;
import eu.stratosphere.pact.common.stubs.Stub;
import eu.stratosphere.pact.common.type.PactRecord;
import eu.stratosphere.pact.runtime.task.AbstractPactTask;
import eu.stratosphere.pact.runtime.task.util.OutputCollector;
import eu.stratosphere.pact.runtime.task.util.TaskConfig;


/**
 * @author Stephan Ewen
 */
public class ChainedMapTask implements ChainedTask
{
	private MapStub mapper;
	
	private Collector collector;
	
	private TaskConfig config;
	
	private String taskName;
	
	private AbstractInvokable parent;
	
	// --------------------------------------------------------------------------------------------
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#setup(eu.stratosphere.pact.runtime.task.util.TaskConfig, eu.stratosphere.nephele.template.AbstractInvokable, eu.stratosphere.pact.common.stubs.Collector)
	 */
	@Override
	public void setup(TaskConfig config, String taskName, AbstractInvokable parent, 
			ClassLoader userCodeClassLoader, Collector output)
	{
		this.config = config;
		this.taskName = taskName;
		this.parent = parent;
		this.collector = output;
		this.mapper = AbstractPactTask.instantiateUserCode(config, userCodeClassLoader, MapStub.class);
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#open()
	 */
	@Override
	public void openTask() throws Exception
	{
		Configuration stubConfig = this.config.getStubParameters();
		stubConfig.setInteger("pact.parallel.task.id", this.parent.getEnvironment().getIndexInSubtaskGroup());
		stubConfig.setInteger("pact.parallel.task.count", this.parent.getEnvironment().getCurrentNumberOfSubtasks());
		if(this.parent.getEnvironment().getTaskName() != null) {
			stubConfig.setString("pact.parallel.task.name", this.parent.getEnvironment().getTaskName());
		}
		AbstractPactTask.openUserCode(this.mapper, stubConfig);
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#closeTask()
	 */
	@Override
	public void closeTask() throws Exception
	{
		AbstractPactTask.closeUserCode(this.mapper);
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#cancelTask()
	 */
	@Override
	public void cancelTask()
	{
		try {
			this.mapper.close();
		} catch (Throwable t) {}
	}
	
	// --------------------------------------------------------------------------------------------
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#getStub()
	 */
	public Stub getStub() {
		return this.mapper;
	}
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.runtime.task.chaining.ChainedTask#getTaskName()
	 */
	public String getTaskName() {
		return this.taskName;
	}
	
	// --------------------------------------------------------------------------------------------
	
	// DW: Start of temporary code
	private int count = 0;
	
	private long consumedPactRecordsInBytes = 0L;
	// DW: End of temporary code
	
	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.common.stubs.Collector#collect(eu.stratosphere.pact.common.type.PactRecord)
	 */
	@Override
	public void collect(PactRecord record)
	{
		// DW: Start of temporary code
		final int recordLength = record.getBinaryLength();
		this.collectedPactRecordsInBytes += recordLength;
		// DW: End of temporary code
		
		try {
			// DW: Start of temporary code
			this.consumedPactRecordsInBytes += recordLength;
			// DW: End of temporary code
			this.mapper.map(record, this.collector);
			// DW: Start of temporary code
			if(++this.count == 10) {
				parent.getEnvironment().reportPACTDataStatistics(
					this.consumedPactRecordsInBytes,
					((OutputCollector) this.collector).getCollectedPactRecordsInBytes());
				this.consumedPactRecordsInBytes = 0L;
				this.count = 0;
			}
			// DW: End of temporary code
			
		}
		catch (Exception ex) {
			throw new ExceptionInChainedStubException(this.taskName, ex);
		}
	}

	/* (non-Javadoc)
	 * @see eu.stratosphere.pact.common.stubs.Collector#close()
	 */
	@Override
	public void close()
	{
		this.collector.close();
	}

	// DW: Start of temporary code
	private long collectedPactRecordsInBytes = 0L;
	
	@Override
	public long getCollectedPactRecordsInBytes() {
		
		final long retVal = this.collectedPactRecordsInBytes;
		this.collectedPactRecordsInBytes = 0L;
		
		return retVal;
	}
	// DW: End of temporary code
}
