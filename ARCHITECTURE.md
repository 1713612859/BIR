# BIR 折扣计算系统架构文档

## 📋 目录
1. [系统概述](#系统概述)
2. [核心架构](#核心架构)
3. [关键组件](#关键组件)
4. [业务流程](#业务流程)
5. [业务规则](#业务规则)
6. [代码组织](#代码组织)

---

## 系统概述

### 系统目标
本系统是一个符合菲律宾税务局（BIR）要求的销售订单折扣和税费计算系统，支持三种业务类型：
- **RETAIL（零售）**
- **DINING（餐饮）**
- **FAST_FOOD（快餐&奶茶）**

### 核心功能
1. **折扣计算**：支持政府折扣（SC、PWD、NAAC、SP、MOV、DIPLOMATIC）和整单折扣
2. **税费计算**：根据商品VAT类型（VATABLE、VAT_EXEMPT、ZERO_RATED）计算税费
3. **税务归集**：将销售额分类为Vatable、VAT Exempt、Zero-Rated
4. **小票打印**：生成符合BIR要求的小票格式

---

## 核心架构

### 架构模式
采用 **策略模式 + 工厂模式 + 上下文模式** 的组合设计：

```
NewDiscountEngine (统一入口)
    ↓
BusinessRuleValidator (业务规则验证)
    ↓
CalculatorFactory (工厂创建计算器)
    ↓
OrderCalculator (策略接口)
    ├── RetailOrderCalculator (零售计算器)
    ├── DiningOrderCalculator (餐饮计算器)
    └── FastFoodOrderCalculator (快餐计算器)
```

### 核心类图

```
┌─────────────────────────────────┐
│   NewDiscountEngine             │  ← 统一入口
└──────────────┬──────────────────┘
               │
               ├──→ CalculationContext (计算上下文)
               │
               ├──→ BusinessRuleValidator (规则验证)
               │
               └──→ CalculatorFactory
                        │
                        ├──→ RetailOrderCalculator
                        │         └──→ ItemLevelCalculator
                        │
                        ├──→ DiningOrderCalculator
                        │         └──→ ItemLevelCalculator
                        │
                        └──→ FastFoodOrderCalculator
                                  └──→ ItemLevelCalculator
```

---

## 关键组件

### 1. NewDiscountEngine（折扣计算引擎）
**位置**：`com.zuno.bir.engine.core.NewDiscountEngine`

**职责**：
- 统一的计算入口
- 协调整个计算流程
- 调用业务规则验证
- 获取对应的计算器
- 计算服务费和最终金额

**关键方法**：
```java
public static void calculateOrder(
    SalesOrder order,
    List<SalesOrderItem> items,
    List<GovernmentDiscount> discounts,
    Store store,
    BigDecimal wholeOrderDiscountAmount,  // 可选：整单金额折扣
    BigDecimal wholeOrderDiscountPercent   // 可选：整单百分比折扣
)
```

### 2. OrderCalculator（订单计算器接口）
**位置**：`com.zuno.bir.engine.core.OrderCalculator`

**职责**：
- 定义统一的计算接口
- 不同业态实现不同的计算逻辑

**实现类**：
- `RetailOrderCalculator`：零售业态计算器
- `DiningOrderCalculator`：餐饮业态计算器
- `FastFoodOrderCalculator`：快餐业态计算器

### 3. ItemLevelCalculator（逐项商品计算器）
**位置**：`com.zuno.bir.engine.ItemLevelCalculator`

**职责**：
- 计算单个商品项的折扣、税费和最终金额
- 执行税务归集（Tax Bucketing）
- 判断商品是否免税

**核心流程**：
1. 计算基础金额（单价×数量 - 常规折扣）
2. 计算原始净价和税额
3. 查找适用的政府折扣
4. 计算折扣金额和税额减免
5. 计算最终支付金额
6. 税务归集（Vatable/VAT Exempt/Zero-Rated）

### 4. DiscountHelpers（折扣帮助类）
**位置**：`com.zuno.bir.engine.DiscountHelpers`

**职责**：
- 提供静态辅助方法
- 定义税率常量
- 验证折扣是否允许
- 获取折扣率
- 计算VAT相关金额

**关键方法**：
- `isDiscountAllowed()`：判断折扣是否允许
- `getDiscountRate()`：获取折扣率
- `calculateNetPrice()`：计算净价
- `calculateVatAmount()`：计算税额

### 5. BusinessRuleValidator（业务规则验证器）
**位置**：`com.zuno.bir.engine.core.BusinessRuleValidator`

**职责**：
- 在计算前验证业务规则
- 确保数据符合各业态的约束

**验证规则**：
- 零售：政府折扣人群只能有一个
- 餐饮：单品折扣只能有一个，整单折扣可以多个（但排除DIPLOMATIC和SP）
- 快餐：只能有一个政府折扣人群

### 6. EnhancedReceiptPrintingService（小票打印服务）
**位置**：`com.zuno.bir.print.EnhancedReceiptPrintingService`

**职责**：
- 生成符合BIR要求的小票格式
- 格式化所有必要的字段
- 处理条件显示逻辑

---

## 业务流程

### 零售业态计算流程

```
1. 创建 CalculationContext
   ↓
2. BusinessRuleValidator.validate() 验证规则
   ↓
3. RetailOrderCalculator.calculate()
   ↓
   ├─ 有政府折扣？
   │   └─→ calculateWithGovernmentDiscount()
   │       └─→ ItemLevelCalculator.processItem() (逐项计算)
   │
   ├─ 有整单折扣？
   │   └─→ calculateWithWholeOrderDiscount()
   │       └─→ Gross - 常规折扣 - 整单折扣，再计算税
   │
   └─ 无折扣？
       └─→ calculateWithoutDiscount()
           └─→ 正常计算税
   ↓
4. 填充订单总额到 SalesOrder
   ↓
5. 计算最终应付金额
```

### 餐饮业态计算流程

```
1. 创建 CalculationContext
   ↓
2. BusinessRuleValidator.validate() 验证规则
   ↓
3. DiningOrderCalculator.calculate()
   ↓
   ├─ 是整单折扣（Group Meal）？
   │   └─→ handleGroupMeal()
   │       └─→ 按人数比例分摊折扣
   │
   └─ 单品折扣？
       └─→ calculateWithGovernmentDiscount()
           └─→ ItemLevelCalculator.processItem() (逐项计算)
   ↓
4. 计算服务费（基于服务费基础）
   ↓
5. 填充订单总额到 SalesOrder
   ↓
6. 计算最终应付金额
```

---

## 业务规则

### 各业态支持的政府折扣类型

| 折扣类型 | RETAIL | DINING | FAST_FOOD |
|---------|--------|--------|-----------|
| SC      | ✅      | ✅      | ✅         |
| PWD     | ✅      | ✅      | ✅         |
| NAAC    | ✅      | ✅      | ✅         |
| SP      | ✅      | ❌      | ❌         |
| MOV     | ✅      | ✅      | ✅         |
| DIPLOMATIC | ✅   | ✅      | ✅         |

### 商品标签与折扣类型的映射

| 商品标签 | 支持的折扣类型 | 折扣率 | 是否免税 |
|---------|--------------|--------|---------|
| MGS     | SC/PWD/NAAC/MOV | 20% | ✅ |
| VBN     | SC/PWD (仅零售) | 5%  | ❌ |
| SP      | SP (仅零售)   | 10% | ✅ |
| NAAC    | NAAC (仅零售) | 20% | - |
| MOV     | MOV (仅零售)  | 20% | - |
| 含税商品 | DIPLOMATIC   | 0%  | ✅ |

### 整单折扣支持情况

| 业态 | 支持整单折扣 | 说明 |
|-----|------------|------|
| RETAIL | ✅ | 支持金额折扣或百分比折扣（非政府折扣） |
| DINING | ✅ | 支持按人数分摊的政府折扣（Group Meal） |
| FAST_FOOD | ❌ | 不支持整单折扣 |

### 零售整单折扣计算逻辑

```
基础金额 = Gross Sales - 常规折扣
整单折扣 = 金额折扣 OR (基础金额 × 百分比折扣)
折扣后金额 = 基础金额 - 整单折扣
税 = 从折扣后金额计算（整单折扣不参与税计算）
```

---

## 代码组织

### 包结构

```
com.zuno.bir
├── engine/
│   ├── core/                    # 核心计算引擎
│   │   ├── NewDiscountEngine.java      # 统一入口
│   │   ├── OrderCalculator.java        # 计算器接口
│   │   ├── CalculationContext.java     # 计算上下文
│   │   ├── CalculationResult.java      # 计算结果
│   │   ├── BusinessRuleValidator.java  # 规则验证器
│   │   ├── CalculatorFactory.java      # 计算器工厂
│   │   └── impl/                       # 计算器实现
│   │       ├── RetailOrderCalculator.java
│   │       ├── DiningOrderCalculator.java
│   │       └── FastFoodOrderCalculator.java
│   │
│   ├── DiscountHelpers.java            # 折扣帮助类
│   ├── ItemLevelCalculator.java        # 逐项计算器
│   │
│   ├── DiscountEngine.java             # ⚠️ 已废弃（旧架构）
│   ├── strategy/                       # ⚠️ 已废弃（旧架构）
│   └── processor/                      # ⚠️ 已废弃（旧架构）
│
├── entity/                      # 实体类
│   ├── SalesOrder.java
│   ├── SalesOrderItem.java
│   ├── GovernmentDiscount.java
│   └── ...
│
├── enums/                       # 枚举类
│   ├── BusinessType.java
│   ├── DiscountType.java
│   └── ItemTag.java
│
└── print/                       # 小票打印
    └── EnhancedReceiptPrintingService.java
```

### 关键文件说明

#### 核心计算文件
- **NewDiscountEngine.java**：统一计算入口，协调整个流程
- **OrderCalculator.java**：计算器接口，定义计算契约
- **RetailOrderCalculator.java**：零售业态计算逻辑
- **DiningOrderCalculator.java**：餐饮业态计算逻辑
- **FastFoodOrderCalculator.java**：快餐业态计算逻辑
- **ItemLevelCalculator.java**：逐项商品计算核心逻辑

#### 辅助文件
- **DiscountHelpers.java**：提供静态辅助方法（税率、折扣率、VAT计算等）
- **BusinessRuleValidator.java**：业务规则验证
- **CalculationContext.java**：封装计算所需的所有数据
- **CalculationResult.java**：封装计算结果

#### 已废弃文件（保留用于向后兼容）
- **DiscountEngine.java**：旧版计算引擎
- **strategy/**：旧版策略模式实现
- **processor/**：旧版处理器实现

---

## 使用示例

### 基本用法（零售业态）

```java
// 1. 创建订单和商品项
SalesOrder order = new SalesOrder();
order.setBusinessType("RETAIL");
order.setSiNumber("000000001");

List<SalesOrderItem> items = new ArrayList<>();
SalesOrderItem item = new SalesOrderItem();
item.setDescription("Test Item");
item.setVatType("VATABLE");
item.setDiscountTag("MGS");
item.setUnitPrice(new BigDecimal("112.00"));
item.setQuantity(1);
items.add(item);

// 2. 创建政府折扣
List<GovernmentDiscount> discounts = new ArrayList<>();
GovernmentDiscount discount = new GovernmentDiscount();
discount.setPersonType("SC");
discounts.add(discount);

// 3. 创建店铺信息
Store store = new Store();
store.setBusinessType("RETAIL");

// 4. 执行计算
NewDiscountEngine.calculateOrder(order, items, discounts, store);

// 5. 查看计算结果
System.out.println("Gross Sales: " + order.getGrossSales());
System.out.println("Government Discount: " + order.getGovDiscountTotal());
System.out.println("Vatable Sales: " + order.getVatableSales());
System.out.println("VAT Amount: " + order.getVatAmount());
System.out.println("Amount Due: " + order.getAmountDue());
```

### 整单折扣用法（零售业态）

```java
// 整单金额折扣（50元）
NewDiscountEngine.calculateOrder(order, items, null, store, 
    new BigDecimal("50.00"), null);

// 整单百分比折扣（10%）
NewDiscountEngine.calculateOrder(order, items, null, store, 
    null, new BigDecimal("0.10"));
```

### 餐饮整单折扣用法

```java
SalesOrder order = new SalesOrder();
order.setBusinessType("DINING");
order.setGroupMeal(true);  // 设置为整单折扣模式
order.setPax(5);            // 设置人数

List<GovernmentDiscount> discounts = new ArrayList<>();
// 2个人MOV折扣
GovernmentDiscount movDiscount = new GovernmentDiscount();
movDiscount.setPersonType("MOV");
movDiscount.setPersonCount(2);
discounts.add(movDiscount);

// 1个人PWD折扣
GovernmentDiscount pwdDiscount = new GovernmentDiscount();
pwdDiscount.setPersonType("PWD");
pwdDiscount.setPersonCount(1);
discounts.add(pwdDiscount);

// 执行计算（会自动按人数比例分摊）
NewDiscountEngine.calculateOrder(order, items, discounts, store);
```

---

## 注意事项

1. **精度处理**：所有金额计算使用 `BigDecimal`，计算精度为4位小数，最终结果保留2位小数
2. **税务归集**：商品会被正确分类到 Vatable、VAT Exempt、Zero-Rated
3. **免税逻辑**：MGS、SP、NAAC、MOV、DIPLOMATIC 商品享受免税待遇
4. **整单折扣**：零售的整单折扣不参与税计算，先计算折扣再计算税
5. **服务费**：仅餐饮业态需要计算服务费，基于服务费基础金额计算

---

## 迁移指南

### 从旧架构迁移到新架构

**旧代码**：
```java
DiscountEngine.calculateOrder(order, items, discounts, store);
```

**新代码**：
```java
NewDiscountEngine.calculateOrder(order, items, discounts, store);
```

**主要变化**：
1. 使用 `NewDiscountEngine` 替代 `DiscountEngine`
2. 新架构提供更好的业务规则验证
3. 新架构提供更清晰的计算上下文和结果封装
4. 支持零售业态的整单折扣（金额/百分比）

---

## 测试

测试文件位于 `src/test/java/com/zuno/bir/`：
- `RetailOrderCalculatorTest.java`：零售业态测试用例
- `DiningOrderCalculatorTest.java`：餐饮业态测试用例
- `FastFoodOrderCalculatorTest.java`：快餐业态测试用例

运行测试：
```bash
mvn test
```

