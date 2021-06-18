/*
 * Copyright (c) 2021-2031, 河北计全科技有限公司 (https://www.jeequan.com & jeequan@126.com).
 * <p>
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE 3.0;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/lgpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jeequan.jeepay.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jeequan.jeepay.core.entity.RefundOrder;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.service.mapper.PayOrderMapper;
import com.jeequan.jeepay.service.mapper.RefundOrderMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;

/**
 * <p>
 * 退款订单表 服务实现类
 * </p>
 *
 * @author [mybatis plus generator]
 * @since 2021-04-27
 */
@Service
public class RefundOrderService extends ServiceImpl<RefundOrderMapper, RefundOrder> {

    @Autowired private PayOrderMapper payOrderMapper;

    /** 查询商户订单 **/
    public RefundOrder queryMchOrder(String mchNo, String mchRefundNo, String refundOrderId){

        if(StringUtils.isNotEmpty(refundOrderId)){
            return getOne(RefundOrder.gw().eq(RefundOrder::getMchNo, mchNo).eq(RefundOrder::getRefundOrderId, refundOrderId));
        }else if(StringUtils.isNotEmpty(mchRefundNo)){
            return getOne(RefundOrder.gw().eq(RefundOrder::getMchNo, mchNo).eq(RefundOrder::getMchRefundNo, mchRefundNo));
        }else{
            return null;
        }
    }


    /** 更新退款单状态  【退款单生成】 --》 【退款中】 **/
    public boolean updateInit2Ing(String refundOrderId){

        RefundOrder updateRecord = new RefundOrder();
        updateRecord.setState(RefundOrder.STATE_ING);

        return update(updateRecord, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getRefundOrderId, refundOrderId).eq(RefundOrder::getState, RefundOrder.STATE_INIT));
    }

    /** 更新退款单状态  【退款中】 --》 【退款成功】 **/
    @Transactional
    public boolean updateIng2Success(String refundOrderId, String channelOrderNo){

        RefundOrder updateRecord = new RefundOrder();
        updateRecord.setState(RefundOrder.STATE_SUCCESS);
        updateRecord.setChannelOrderNo(channelOrderNo);
        updateRecord.setSuccessTime(new Date());

        //1. 更新退款订单表数据
        if(! update(updateRecord, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getRefundOrderId, refundOrderId).eq(RefundOrder::getState, RefundOrder.STATE_ING))
        ){
            return false;
        }

        //2. 更新订单表数据
        RefundOrder refundOrder = getOne(RefundOrder.gw().select(RefundOrder::getPayOrderId, RefundOrder::getRefundAmount).eq(RefundOrder::getRefundOrderId, refundOrderId));
        int updateCount = payOrderMapper.updateRefundAmountAndCount(refundOrder.getPayOrderId(), refundOrder.getRefundAmount());
        if(updateCount <= 0){
            throw new BizException("更新订单数据异常");
        }

        return true;
    }


    /** 更新退款单状态  【退款中】 --》 【退款失败】 **/
    @Transactional
    public boolean updateIng2Fail(String refundOrderId, String channelOrderNo, String channelErrCode, String channelErrMsg){

        RefundOrder updateRecord = new RefundOrder();
        updateRecord.setState(RefundOrder.STATE_FAIL);
        updateRecord.setErrCode(channelErrCode);
        updateRecord.setErrMsg(channelErrMsg);
        updateRecord.setChannelOrderNo(channelOrderNo);

        return update(updateRecord, new LambdaUpdateWrapper<RefundOrder>()
                .eq(RefundOrder::getRefundOrderId, refundOrderId).eq(RefundOrder::getState, RefundOrder.STATE_ING));
    }


    /** 更新退款单状态  【退款中】 --》 【退款成功/退款失败】 **/
    @Transactional
    public boolean updateIng2SuccessOrFail(String refundOrderId, Byte updateState, String channelOrderNo, String channelErrCode, String channelErrMsg){

        if(updateState == RefundOrder.STATE_ING){
            return true;
        }else if(updateState == RefundOrder.STATE_SUCCESS){
            return updateIng2Success(refundOrderId, channelOrderNo);
        }else if(updateState == RefundOrder.STATE_FAIL){
            return updateIng2Fail(refundOrderId, channelOrderNo, channelErrCode, channelErrMsg);
        }
        return false;
    }


    /** 更新退款单为 关闭状态 **/
    public Integer updateOrderExpired(){

        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setState(RefundOrder.STATE_CLOSED);

        return baseMapper.update(refundOrder,
                RefundOrder.gw()
                        .in(RefundOrder::getState, Arrays.asList(RefundOrder.STATE_INIT, RefundOrder.STATE_ING))
                        .le(RefundOrder::getExpiredTime, new Date())
        );
    }


}
