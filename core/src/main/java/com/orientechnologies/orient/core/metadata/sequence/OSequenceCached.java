/*
 *
 *  *  Copyright 2014 OrientDB LTD (info(at)orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://www.orientdb.com
 *
 */
package com.orientechnologies.orient.core.metadata.sequence;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author Matan Shukry (matanshukry@gmail.com)
 * @since 3/3/2015
 */
public class OSequenceCached extends OSequence {
  private static final String FIELD_CACHE = "cache";
  private long    cacheStart;
  private long    cacheEnd;
  private boolean firstCache;
  private int     increment;
  private Long limitValue = null;
  private Long              startValue;
  private SequenceOrderType orderType;
  private boolean           recyclable;
  private String name = null;

  private Set<Long> generated = new HashSet<>();
  
  public OSequenceCached() {
    this(null, null);
  }

  public OSequenceCached(final ODocument iDocument) {
    this(iDocument, null);
  }

  public OSequenceCached(final ODocument iDocument, OSequence.CreateParams params) {
    super(iDocument, params);
    //this is extension of initSequence
    if (iDocument == null){
      if (params == null) {
        params = new CreateParams().setDefaults();
      }
      setCacheSize(params.cacheSize);
      cacheStart = cacheEnd = 0L;
      allocateCache(getCacheSize());
    }
    if (iDocument != null) {
      firstCache = true;
      cacheStart = cacheEnd = getValue(iDocument);
    }
  }

  @Override
  public synchronized boolean updateParams(OSequence.CreateParams params, boolean executeViaDistributed) throws ExecutionException, InterruptedException{
    boolean any = super.updateParams(params, executeViaDistributed);
    if (!executeViaDistributed){
      if (params.cacheSize != null && this.getCacheSize() != params.cacheSize) {
        this.setCacheSize(params.cacheSize);
        any = true;
      }            
      
      firstCache = true;
      save();
    }    
    return any;
  }  

  private void doRecycle() {
    if (recyclable) {
      reloadSequence();
      setValue(getStart());
      allocateCache(getCacheSize());
    } else {
      throw new OSequenceLimitReachedException("Limit reached");
    }
  }

  private void reloadCrucialValues() {
    increment = getIncrement();
    limitValue = getLimitValue();
    orderType = getOrderType();
    recyclable = getRecyclable();
    startValue = getStart();
    if (name == null) {
      name = getName();
    }
  }

  private boolean signalToAllocateCache(){
    if (orderType == SequenceOrderType.ORDER_POSITIVE) {
      if (cacheStart + increment > cacheEnd && !(limitValue != null && cacheStart + increment > limitValue)){
        return true;
      }
    }
    else{
      if (cacheStart - increment < cacheEnd && !(limitValue != null && cacheStart - increment < limitValue)) {
        return true;
      }
    }
    return false;
  }
  
  private <T> T sendSequenceActionSetAndNext(long value) throws ExecutionException, InterruptedException{    
    OSequenceAction action = new OSequenceAction(getName(), value);
    return getDocument().getDatabase().sendSequenceAction(action);    
  }
  
  //want to be atomic
  //first set new current value then call next
  public long nextWithNewCurrentValue(long currentValue, boolean executeViaDistributed) throws OSequenceLimitReachedException, ExecutionException, InterruptedException{
    if (!executeViaDistributed){
      //we don't want synchronization on whole method, because called with executeViaDistributed == true
      //will later call nextWithNewCurrentValue with parameter executeViaDistributed == false
      //and that will cause deadlock
      synchronized(this){
        cacheStart = currentValue;
        return nextWork();
      }
    }
    else{
      return sendSequenceActionSetAndNext(currentValue);
    }
  }
  
  @Override
  protected boolean shouldGoOverDistrtibute(){
    return isOnDistributted() && (replicationProtocolVersion == 2) && signalToAllocateCache();
  }
  
  @Override
  public long next() throws OSequenceLimitReachedException, ExecutionException, InterruptedException{
    boolean shouldGoOverDistributted = shouldGoOverDistrtibute();
    if (shouldGoOverDistributted){
      return nextWithNewCurrentValue(cacheStart, true);
    }
    return nextWork();
  }
  
  @Override
  public long nextWork() throws OSequenceLimitReachedException {           
    return callRetry(signalToAllocateCache() || getCrucialValueChanged(), new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        synchronized (OSequenceCached.this) {          
          boolean detectedCrucialValueChange = false;
          if (getCrucialValueChanged()) {
            reloadCrucialValues();
            detectedCrucialValueChange = true;
          }
          if (orderType == SequenceOrderType.ORDER_POSITIVE) {
            if (signalToAllocateCache()) {
              boolean cachedbefore = !firstCache;
              allocateCache(getCacheSize());
              if (!cachedbefore) {
                if (limitValue != null && cacheStart + increment > limitValue) {
                  doRecycle();
                } else {
                  cacheStart = cacheStart + increment;                  
                }
              }
            } else if (limitValue != null && cacheStart + increment > limitValue) {
              doRecycle();
            } else {
              cacheStart = cacheStart + increment;              
            }
          } else {
            if (signalToAllocateCache()) {
              boolean cachedbefore = !firstCache;
              allocateCache(getCacheSize());
              if (!cachedbefore) {
                if (limitValue != null && cacheStart - increment < limitValue) {
                  doRecycle();
                } else {
                  cacheStart = cacheStart - increment;
                }
              }
            } else if (limitValue != null && cacheStart - increment < limitValue) {
              doRecycle();
            } else {
              cacheStart = cacheStart - increment;
            }
          }

          if (detectedCrucialValueChange) {
            setCrucialValueChanged(false);
          }

          if (limitValue != null && !recyclable) {
            float tillEnd = Math.abs(limitValue - cacheStart) / (float) increment;
            float delta = Math.abs(limitValue - startValue) / (float) increment;
            //warning on 1%
            if ((float) tillEnd <= ((float) delta / 100.f) || tillEnd <= 1) {
              String warningMessage =
                  "Non-recyclable sequence: " + name + " reaching limt, current value: " + cacheStart + " limit value: "
                      + limitValue + " with step: " + increment;
              OLogManager.instance().warn(this, warningMessage);
            }
          }

          firstCache = false;          
          return cacheStart;
        }
      }
    }, "next");        
  }

  @Override
  protected synchronized long currentWork() {
    return this.cacheStart;
  }

  @Override
  public long resetWork() {
    return callRetry(true, new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        synchronized (OSequenceCached.this) {
          long newValue = getStart();
          setValue(newValue);
          save();
          firstCache = true;
          allocateCache(getCacheSize());
          return newValue;
        }
      }
    }, "reset");          
  }

  @Override
  public SEQUENCE_TYPE getSequenceType() {
    return SEQUENCE_TYPE.CACHED;
  }

  public final int getCacheSize() {
    return getDocument().field(FIELD_CACHE, OType.INTEGER);
  }

  public final void setCacheSize(int cacheSize) {
    getDocument().field(FIELD_CACHE, cacheSize);
  }

  private void allocateCache(int cacheSize) {        
    if (getCrucialValueChanged()) {
      reloadCrucialValues();
      setCrucialValueChanged(false);
    }
    SequenceOrderType orederType = getOrderType();
    long value = getValue();
    long newValue;
    if (orederType == SequenceOrderType.ORDER_POSITIVE) {
      newValue = value + (getIncrement() * cacheSize);
      if (limitValue != null && newValue > limitValue) {
        newValue = limitValue;
      }
    } else {
      newValue = value - (getIncrement() * cacheSize);
      if (limitValue != null && newValue < limitValue) {
        newValue = limitValue;
      }
    }    
    setValue(newValue);    
    save();

    this.cacheStart = value;
    if (orederType == SequenceOrderType.ORDER_POSITIVE) {
      this.cacheEnd = newValue - 1;
    } else {
      this.cacheEnd = newValue + 1;
    }
    firstCache = false;
  }   

}