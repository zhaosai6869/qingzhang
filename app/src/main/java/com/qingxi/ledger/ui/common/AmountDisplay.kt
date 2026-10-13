package com.qingxi.ledger.ui.common

import com.qingxi.ledger.util.Money

/** 汇总金额的显示口径，统一按元显示。 */
object AmountDisplay {

    fun format(minor: Long): String = Money.format(minor)
}
