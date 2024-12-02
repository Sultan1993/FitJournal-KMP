#import <Foundation/NSArray.h>
#import <Foundation/NSDictionary.h>
#import <Foundation/NSError.h>
#import <Foundation/NSObject.h>
#import <Foundation/NSSet.h>
#import <Foundation/NSString.h>
#import <Foundation/NSValue.h>

@class FJKMPBodyMeasurements, FJKMPKotlinUnit, FJKMPRuntimeTransacterTransaction, FJKMPKotlinThrowable, FJKMPRuntimeBaseTransacterImpl, FJKMPRuntimeTransacterImpl, FJKMPRuntimeQuery<__covariant RowType>, FJKMPCategories, FJKMPExercises, FJKMPBodyMeasurementsQueries, FJKMPCategoryQueries, FJKMPExercisesQueries, FJKMPNotesQueries, FJKMPPhotoMeasurementsQueries, FJKMPFitJournalDatabaseCompanion, FJKMPNotes, FJKMPPhotoMeasurements, FJKMPDBCategoryObject, FJKMPExerciseDBMapper, FJKMPDBExerciseObject, FJKMPCategoriesDBDataSource, FJKMPDBBodyMeasurementObject, FJKMPKotlinx_datetimeLocalDateTime, FJKMPDBPhotoMeasurementObject, FJKMPKotlinx_datetimeLocalDate, FJKMPDBNoteObject, FJKMPKotlinArray<T>, FJKMPRuntimeExecutableQuery<__covariant RowType>, FJKMPRuntimeAfterVersion, FJKMPKotlinx_datetimeLocalTime, FJKMPKotlinx_datetimeMonth, FJKMPKotlinx_datetimeLocalDateTimeCompanion, FJKMPKotlinx_datetimeDayOfWeek, FJKMPKotlinx_datetimeLocalDateCompanion, FJKMPKotlinByteArray, FJKMPKotlinException, FJKMPKotlinRuntimeException, FJKMPKotlinIllegalStateException, FJKMPKotlinx_datetimeLocalTimeCompanion, FJKMPKotlinEnumCompanion, FJKMPKotlinEnum<E>, FJKMPKotlinByteIterator, FJKMPKotlinx_datetimePadding, FJKMPKotlinx_datetimeDayOfWeekNames, FJKMPKotlinx_datetimeMonthNames, FJKMPKotlinx_datetimeDayOfWeekNamesCompanion, FJKMPKotlinx_datetimeMonthNamesCompanion, FJKMPKotlinx_serialization_coreSerializersModule, FJKMPKotlinx_serialization_coreSerialKind, FJKMPKotlinNothing;

@protocol FJKMPRuntimeSqlDriver, FJKMPRuntimeTransactionWithoutReturn, FJKMPRuntimeTransactionWithReturn, FJKMPRuntimeTransacterBase, FJKMPRuntimeTransacter, FJKMPFitJournalDatabase, FJKMPRuntimeSqlSchema, FJKMPKotlinx_coroutines_coreFlow, FJKMPRuntimeQueryListener, FJKMPRuntimeQueryResult, FJKMPRuntimeSqlPreparedStatement, FJKMPRuntimeSqlCursor, FJKMPRuntimeCloseable, FJKMPRuntimeTransactionCallbacks, FJKMPKotlinx_coroutines_coreFlowCollector, FJKMPKotlinComparable, FJKMPKotlinIterator, FJKMPKotlinx_datetimeDateTimeFormat, FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDateTime, FJKMPKotlinx_serialization_coreKSerializer, FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDate, FJKMPKotlinx_datetimeDateTimeFormatBuilderWithTime, FJKMPKotlinAppendable, FJKMPKotlinx_datetimeDateTimeFormatBuilder, FJKMPKotlinx_serialization_coreEncoder, FJKMPKotlinx_serialization_coreSerialDescriptor, FJKMPKotlinx_serialization_coreSerializationStrategy, FJKMPKotlinx_serialization_coreDecoder, FJKMPKotlinx_serialization_coreDeserializationStrategy, FJKMPKotlinx_serialization_coreCompositeEncoder, FJKMPKotlinAnnotation, FJKMPKotlinx_serialization_coreCompositeDecoder, FJKMPKotlinx_serialization_coreSerializersModuleCollector, FJKMPKotlinKClass, FJKMPKotlinKDeclarationContainer, FJKMPKotlinKAnnotatedElement, FJKMPKotlinKClassifier;

NS_ASSUME_NONNULL_BEGIN
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunknown-warning-option"
#pragma clang diagnostic ignored "-Wincompatible-property-type"
#pragma clang diagnostic ignored "-Wnullability"

#pragma push_macro("_Nullable_result")
#if !__has_feature(nullability_nullable_result)
#undef _Nullable_result
#define _Nullable_result _Nullable
#endif

__attribute__((swift_name("KotlinBase")))
@interface FJKMPBase : NSObject
- (instancetype)init __attribute__((unavailable));
+ (instancetype)new __attribute__((unavailable));
+ (void)initialize __attribute__((objc_requires_super));
@end

@interface FJKMPBase (FJKMPBaseCopying) <NSCopying>
@end

__attribute__((swift_name("KotlinMutableSet")))
@interface FJKMPMutableSet<ObjectType> : NSMutableSet<ObjectType>
@end

__attribute__((swift_name("KotlinMutableDictionary")))
@interface FJKMPMutableDictionary<KeyType, ObjectType> : NSMutableDictionary<KeyType, ObjectType>
@end

@interface NSError (NSErrorFJKMPKotlinException)
@property (readonly) id _Nullable kotlinException;
@end

__attribute__((swift_name("KotlinNumber")))
@interface FJKMPNumber : NSNumber
- (instancetype)initWithChar:(char)value __attribute__((unavailable));
- (instancetype)initWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
- (instancetype)initWithShort:(short)value __attribute__((unavailable));
- (instancetype)initWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
- (instancetype)initWithInt:(int)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
- (instancetype)initWithLong:(long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
- (instancetype)initWithLongLong:(long long)value __attribute__((unavailable));
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
- (instancetype)initWithFloat:(float)value __attribute__((unavailable));
- (instancetype)initWithDouble:(double)value __attribute__((unavailable));
- (instancetype)initWithBool:(BOOL)value __attribute__((unavailable));
- (instancetype)initWithInteger:(NSInteger)value __attribute__((unavailable));
- (instancetype)initWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
+ (instancetype)numberWithChar:(char)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedChar:(unsigned char)value __attribute__((unavailable));
+ (instancetype)numberWithShort:(short)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedShort:(unsigned short)value __attribute__((unavailable));
+ (instancetype)numberWithInt:(int)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInt:(unsigned int)value __attribute__((unavailable));
+ (instancetype)numberWithLong:(long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLong:(unsigned long)value __attribute__((unavailable));
+ (instancetype)numberWithLongLong:(long long)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value __attribute__((unavailable));
+ (instancetype)numberWithFloat:(float)value __attribute__((unavailable));
+ (instancetype)numberWithDouble:(double)value __attribute__((unavailable));
+ (instancetype)numberWithBool:(BOOL)value __attribute__((unavailable));
+ (instancetype)numberWithInteger:(NSInteger)value __attribute__((unavailable));
+ (instancetype)numberWithUnsignedInteger:(NSUInteger)value __attribute__((unavailable));
@end

__attribute__((swift_name("KotlinByte")))
@interface FJKMPByte : FJKMPNumber
- (instancetype)initWithChar:(char)value;
+ (instancetype)numberWithChar:(char)value;
@end

__attribute__((swift_name("KotlinUByte")))
@interface FJKMPUByte : FJKMPNumber
- (instancetype)initWithUnsignedChar:(unsigned char)value;
+ (instancetype)numberWithUnsignedChar:(unsigned char)value;
@end

__attribute__((swift_name("KotlinShort")))
@interface FJKMPShort : FJKMPNumber
- (instancetype)initWithShort:(short)value;
+ (instancetype)numberWithShort:(short)value;
@end

__attribute__((swift_name("KotlinUShort")))
@interface FJKMPUShort : FJKMPNumber
- (instancetype)initWithUnsignedShort:(unsigned short)value;
+ (instancetype)numberWithUnsignedShort:(unsigned short)value;
@end

__attribute__((swift_name("KotlinInt")))
@interface FJKMPInt : FJKMPNumber
- (instancetype)initWithInt:(int)value;
+ (instancetype)numberWithInt:(int)value;
@end

__attribute__((swift_name("KotlinUInt")))
@interface FJKMPUInt : FJKMPNumber
- (instancetype)initWithUnsignedInt:(unsigned int)value;
+ (instancetype)numberWithUnsignedInt:(unsigned int)value;
@end

__attribute__((swift_name("KotlinLong")))
@interface FJKMPLong : FJKMPNumber
- (instancetype)initWithLongLong:(long long)value;
+ (instancetype)numberWithLongLong:(long long)value;
@end

__attribute__((swift_name("KotlinULong")))
@interface FJKMPULong : FJKMPNumber
- (instancetype)initWithUnsignedLongLong:(unsigned long long)value;
+ (instancetype)numberWithUnsignedLongLong:(unsigned long long)value;
@end

__attribute__((swift_name("KotlinFloat")))
@interface FJKMPFloat : FJKMPNumber
- (instancetype)initWithFloat:(float)value;
+ (instancetype)numberWithFloat:(float)value;
@end

__attribute__((swift_name("KotlinDouble")))
@interface FJKMPDouble : FJKMPNumber
- (instancetype)initWithDouble:(double)value;
+ (instancetype)numberWithDouble:(double)value;
@end

__attribute__((swift_name("KotlinBoolean")))
@interface FJKMPBoolean : FJKMPNumber
- (instancetype)initWithBool:(BOOL)value;
+ (instancetype)numberWithBool:(BOOL)value;
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("BodyMeasurements")))
@interface FJKMPBodyMeasurements : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value_:(double)value_ comment:(NSString * _Nullable)comment measurementDate:(NSString *)measurementDate createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("init(uuid:remoteId:userId:diaryId:type:value_:comment:measurementDate:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPBodyMeasurements *)doCopyUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value_:(double)value_ comment:(NSString * _Nullable)comment measurementDate:(NSString *)measurementDate createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("doCopy(uuid:remoteId:userId:diaryId:type:value_:comment:measurementDate:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable comment __attribute__((swift_name("comment")));
@property (readonly) NSString *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) NSString *diaryId __attribute__((swift_name("diaryId")));
@property (readonly) NSString *measurementDate __attribute__((swift_name("measurementDate")));
@property (readonly) NSString * _Nullable remoteId __attribute__((swift_name("remoteId")));
@property (readonly) NSString *type __attribute__((swift_name("type")));
@property (readonly) NSString *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@property (readonly) double value_ __attribute__((swift_name("value_")));
@end

__attribute__((swift_name("RuntimeBaseTransacterImpl")))
@interface FJKMPRuntimeBaseTransacterImpl : FJKMPBase
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));

/**
 * @note This method has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
- (NSString *)createArgumentsCount:(int32_t)count __attribute__((swift_name("createArguments(count:)")));

/**
 * @note This method has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
- (void)notifyQueriesIdentifier:(int32_t)identifier tableProvider:(void (^)(FJKMPKotlinUnit *(^)(NSString *)))tableProvider __attribute__((swift_name("notifyQueries(identifier:tableProvider:)")));

/**
 * @note This method has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
- (id _Nullable)postTransactionCleanupTransaction:(FJKMPRuntimeTransacterTransaction *)transaction enclosing:(FJKMPRuntimeTransacterTransaction * _Nullable)enclosing thrownException:(FJKMPKotlinThrowable * _Nullable)thrownException returnValue:(id _Nullable)returnValue __attribute__((swift_name("postTransactionCleanup(transaction:enclosing:thrownException:returnValue:)")));

/**
 * @note This property has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
@property (readonly) id<FJKMPRuntimeSqlDriver> driver __attribute__((swift_name("driver")));
@end

__attribute__((swift_name("RuntimeTransacterBase")))
@protocol FJKMPRuntimeTransacterBase
@required
@end

__attribute__((swift_name("RuntimeTransacter")))
@protocol FJKMPRuntimeTransacter <FJKMPRuntimeTransacterBase>
@required
- (void)transactionNoEnclosing:(BOOL)noEnclosing body:(void (^)(id<FJKMPRuntimeTransactionWithoutReturn>))body __attribute__((swift_name("transaction(noEnclosing:body:)")));
- (id _Nullable)transactionWithResultNoEnclosing:(BOOL)noEnclosing bodyWithReturn:(id _Nullable (^)(id<FJKMPRuntimeTransactionWithReturn>))bodyWithReturn __attribute__((swift_name("transactionWithResult(noEnclosing:bodyWithReturn:)")));
@end

__attribute__((swift_name("RuntimeTransacterImpl")))
@interface FJKMPRuntimeTransacterImpl : FJKMPRuntimeBaseTransacterImpl <FJKMPRuntimeTransacter>
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)transactionNoEnclosing:(BOOL)noEnclosing body:(void (^)(id<FJKMPRuntimeTransactionWithoutReturn>))body __attribute__((swift_name("transaction(noEnclosing:body:)")));
- (id _Nullable)transactionWithResultNoEnclosing:(BOOL)noEnclosing bodyWithReturn:(id _Nullable (^)(id<FJKMPRuntimeTransactionWithReturn>))bodyWithReturn __attribute__((swift_name("transactionWithResult(noEnclosing:bodyWithReturn:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("BodyMeasurementsQueries")))
@interface FJKMPBodyMeasurementsQueries : FJKMPRuntimeTransacterImpl
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)createBodyMeasurementUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value_:(double)value_ comment:(NSString * _Nullable)comment measurementDate:(NSString *)measurementDate createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("createBodyMeasurement(uuid:remoteId:userId:diaryId:type:value_:comment:measurementDate:createdDate:updatedDate:)")));
- (void)deleteBodyMeasurementUuid:(NSString *)uuid __attribute__((swift_name("deleteBodyMeasurement(uuid:)")));
- (void)deleteBodyMeasurements __attribute__((swift_name("deleteBodyMeasurements()")));
- (void)deleteBodyMeasurementsByDiaryIdDiaryId:(NSString *)diaryId __attribute__((swift_name("deleteBodyMeasurementsByDiaryId(diaryId:)")));
- (void)deleteBodyMeasurementsByUserIdUserId:(NSString *)userId __attribute__((swift_name("deleteBodyMeasurementsByUserId(userId:)")));
- (FJKMPRuntimeQuery<FJKMPBodyMeasurements *> *)getBodyMeasurementByIdUuid:(NSString *)uuid __attribute__((swift_name("getBodyMeasurementById(uuid:)")));
- (FJKMPRuntimeQuery<id> *)getBodyMeasurementByIdUuid:(NSString *)uuid mapper:(id (^)(NSString *, NSString * _Nullable, NSString *, NSString *, NSString *, FJKMPDouble *, NSString * _Nullable, NSString *, NSString *, NSString *))mapper __attribute__((swift_name("getBodyMeasurementById(uuid:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPBodyMeasurements *> *)getBodyMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getBodyMeasurements(userId:diaryId:)")));
- (FJKMPRuntimeQuery<id> *)getBodyMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId mapper:(id (^)(NSString *, NSString * _Nullable, NSString *, NSString *, NSString *, FJKMPDouble *, NSString * _Nullable, NSString *, NSString *, NSString *))mapper __attribute__((swift_name("getBodyMeasurements(userId:diaryId:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPBodyMeasurements *> *)getBodyMeasurementsByTypeUserId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type __attribute__((swift_name("getBodyMeasurementsByType(userId:diaryId:type:)")));
- (FJKMPRuntimeQuery<id> *)getBodyMeasurementsByTypeUserId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type mapper:(id (^)(NSString *, NSString * _Nullable, NSString *, NSString *, NSString *, FJKMPDouble *, NSString * _Nullable, NSString *, NSString *, NSString *))mapper __attribute__((swift_name("getBodyMeasurementsByType(userId:diaryId:type:mapper:)")));
- (void)updateBodyMeasurementValue_:(double)value_ comment:(NSString * _Nullable)comment measurementDate:(NSString *)measurementDate updatedDate:(NSString *)updatedDate uuid:(NSString *)uuid __attribute__((swift_name("updateBodyMeasurement(value_:comment:measurementDate:updatedDate:uuid:)")));
- (void)updateBodyMeasurementRemoteIdRemoteId:(NSString * _Nullable)remoteId uuid:(NSString *)uuid __attribute__((swift_name("updateBodyMeasurementRemoteId(remoteId:uuid:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Categories")))
@interface FJKMPCategories : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int64_t)type details:(NSString * _Nullable)details __attribute__((swift_name("init(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)"))) __attribute__((objc_designated_initializer));
- (FJKMPCategories *)doCopyUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int64_t)type details:(NSString * _Nullable)details __attribute__((swift_name("doCopy(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable details __attribute__((swift_name("details")));
@property (readonly) NSString *nameEn __attribute__((swift_name("nameEn")));
@property (readonly) NSString *nameRu __attribute__((swift_name("nameRu")));
@property (readonly) NSString *nameUk __attribute__((swift_name("nameUk")));
@property (readonly) NSString *remoteId __attribute__((swift_name("remoteId")));
@property (readonly) int64_t type __attribute__((swift_name("type")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("CategoryQueries")))
@interface FJKMPCategoryQueries : FJKMPRuntimeTransacterImpl
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)createCategoryUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int64_t)type details:(NSString * _Nullable)details __attribute__((swift_name("createCategory(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)")));
- (void)deleteAllCategories __attribute__((swift_name("deleteAllCategories()")));
- (FJKMPRuntimeQuery<FJKMPCategories *> *)getAllCategories __attribute__((swift_name("getAllCategories()")));
- (FJKMPRuntimeQuery<id> *)getAllCategoriesMapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString *, FJKMPLong *, NSString * _Nullable))mapper __attribute__((swift_name("getAllCategories(mapper:)")));
- (FJKMPRuntimeQuery<FJKMPCategories *> *)getCategoryByRemoteIdRemoteId:(NSString *)remoteId __attribute__((swift_name("getCategoryByRemoteId(remoteId:)")));
- (FJKMPRuntimeQuery<id> *)getCategoryByRemoteIdRemoteId:(NSString *)remoteId mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString *, FJKMPLong *, NSString * _Nullable))mapper __attribute__((swift_name("getCategoryByRemoteId(remoteId:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPCategories *> *)getCategoryByUuidUuid:(NSString *)uuid __attribute__((swift_name("getCategoryByUuid(uuid:)")));
- (FJKMPRuntimeQuery<id> *)getCategoryByUuidUuid:(NSString *)uuid mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString *, FJKMPLong *, NSString * _Nullable))mapper __attribute__((swift_name("getCategoryByUuid(uuid:mapper:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Exercises")))
@interface FJKMPExercises : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk details:(NSString * _Nullable)details image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 resultType:(int64_t)resultType primaryCategoryUuid:(NSString *)primaryCategoryUuid secondaryCategoryUuids:(NSString * _Nullable)secondaryCategoryUuids global:(BOOL)global __attribute__((swift_name("init(uuid:remoteId:nameEn:nameRu:nameUk:details:image1:image2:resultType:primaryCategoryUuid:secondaryCategoryUuids:global:)"))) __attribute__((objc_designated_initializer));
- (FJKMPExercises *)doCopyUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk details:(NSString * _Nullable)details image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 resultType:(int64_t)resultType primaryCategoryUuid:(NSString *)primaryCategoryUuid secondaryCategoryUuids:(NSString * _Nullable)secondaryCategoryUuids global:(BOOL)global __attribute__((swift_name("doCopy(uuid:remoteId:nameEn:nameRu:nameUk:details:image1:image2:resultType:primaryCategoryUuid:secondaryCategoryUuids:global:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable details __attribute__((swift_name("details")));
@property (readonly) BOOL global __attribute__((swift_name("global")));
@property (readonly) NSString * _Nullable image1 __attribute__((swift_name("image1")));
@property (readonly) NSString * _Nullable image2 __attribute__((swift_name("image2")));
@property (readonly) NSString *nameEn __attribute__((swift_name("nameEn")));
@property (readonly) NSString *nameRu __attribute__((swift_name("nameRu")));
@property (readonly) NSString * _Nullable nameUk __attribute__((swift_name("nameUk")));
@property (readonly) NSString *primaryCategoryUuid __attribute__((swift_name("primaryCategoryUuid")));
@property (readonly) NSString *remoteId __attribute__((swift_name("remoteId")));
@property (readonly) int64_t resultType __attribute__((swift_name("resultType")));
@property (readonly) NSString * _Nullable secondaryCategoryUuids __attribute__((swift_name("secondaryCategoryUuids")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ExercisesQueries")))
@interface FJKMPExercisesQueries : FJKMPRuntimeTransacterImpl
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)createExerciseUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk details:(NSString * _Nullable)details image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 resultType:(int64_t)resultType primaryCategoryUuid:(NSString *)primaryCategoryUuid secondaryCategoryUuids:(NSString * _Nullable)secondaryCategoryUuids global:(BOOL)global __attribute__((swift_name("createExercise(uuid:remoteId:nameEn:nameRu:nameUk:details:image1:image2:resultType:primaryCategoryUuid:secondaryCategoryUuids:global:)")));
- (void)deleteAllExercises __attribute__((swift_name("deleteAllExercises()")));
- (void)deleteExerciseUuid:(NSString *)uuid __attribute__((swift_name("deleteExercise(uuid:)")));
- (FJKMPRuntimeQuery<FJKMPExercises *> *)getAllExercises __attribute__((swift_name("getAllExercises()")));
- (FJKMPRuntimeQuery<id> *)getAllExercisesMapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, FJKMPLong *, NSString *, NSString * _Nullable, FJKMPBoolean *))mapper __attribute__((swift_name("getAllExercises(mapper:)")));
- (FJKMPRuntimeQuery<FJKMPExercises *> *)getExerciseByRemoteIdRemoteId:(NSString *)remoteId __attribute__((swift_name("getExerciseByRemoteId(remoteId:)")));
- (FJKMPRuntimeQuery<id> *)getExerciseByRemoteIdRemoteId:(NSString *)remoteId mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, FJKMPLong *, NSString *, NSString * _Nullable, FJKMPBoolean *))mapper __attribute__((swift_name("getExerciseByRemoteId(remoteId:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPExercises *> *)getExerciseByUuidUuid:(NSString *)uuid __attribute__((swift_name("getExerciseByUuid(uuid:)")));
- (FJKMPRuntimeQuery<id> *)getExerciseByUuidUuid:(NSString *)uuid mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, FJKMPLong *, NSString *, NSString * _Nullable, FJKMPBoolean *))mapper __attribute__((swift_name("getExerciseByUuid(uuid:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPExercises *> *)getExercisesByCategoryUuidPrimaryCategoryUuid:(NSString *)primaryCategoryUuid __attribute__((swift_name("getExercisesByCategoryUuid(primaryCategoryUuid:)")));
- (FJKMPRuntimeQuery<id> *)getExercisesByCategoryUuidPrimaryCategoryUuid:(NSString *)primaryCategoryUuid mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, NSString * _Nullable, FJKMPLong *, NSString *, NSString * _Nullable, FJKMPBoolean *))mapper __attribute__((swift_name("getExercisesByCategoryUuid(primaryCategoryUuid:mapper:)")));
- (void)updateExerciseRemoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk details:(NSString * _Nullable)details resultType:(int64_t)resultType primaryCategoryUuid:(NSString *)primaryCategoryUuid secondaryCategoryUuids:(NSString * _Nullable)secondaryCategoryUuids uuid:(NSString *)uuid __attribute__((swift_name("updateExercise(remoteId:nameEn:nameRu:nameUk:details:resultType:primaryCategoryUuid:secondaryCategoryUuids:uuid:)")));
@end

__attribute__((swift_name("FitJournalDatabase")))
@protocol FJKMPFitJournalDatabase <FJKMPRuntimeTransacter>
@required
@property (readonly) FJKMPBodyMeasurementsQueries *bodyMeasurementsQueries __attribute__((swift_name("bodyMeasurementsQueries")));
@property (readonly) FJKMPCategoryQueries *categoryQueries __attribute__((swift_name("categoryQueries")));
@property (readonly) FJKMPExercisesQueries *exercisesQueries __attribute__((swift_name("exercisesQueries")));
@property (readonly) FJKMPNotesQueries *notesQueries __attribute__((swift_name("notesQueries")));
@property (readonly) FJKMPPhotoMeasurementsQueries *photoMeasurementsQueries __attribute__((swift_name("photoMeasurementsQueries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("FitJournalDatabaseCompanion")))
@interface FJKMPFitJournalDatabaseCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPFitJournalDatabaseCompanion *shared __attribute__((swift_name("shared")));
- (id<FJKMPFitJournalDatabase>)invokeDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("invoke(driver:)")));
@property (readonly) id<FJKMPRuntimeSqlSchema> Schema __attribute__((swift_name("Schema")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Notes")))
@interface FJKMPNotes : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("init(uuid:userId:text:isPinned:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPNotes *)doCopyUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("doCopy(uuid:userId:text:isPinned:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) BOOL isPinned __attribute__((swift_name("isPinned")));
@property (readonly) NSString *text __attribute__((swift_name("text")));
@property (readonly) NSString *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("NotesQueries")))
@interface FJKMPNotesQueries : FJKMPRuntimeTransacterImpl
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)createNoteUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("createNote(uuid:userId:text:isPinned:createdDate:updatedDate:)")));
- (void)deleteAllNotes __attribute__((swift_name("deleteAllNotes()")));
- (void)deleteNoteUuid:(NSString *)uuid __attribute__((swift_name("deleteNote(uuid:)")));
- (void)deleteUserNotesUserId:(NSString *)userId __attribute__((swift_name("deleteUserNotes(userId:)")));
- (FJKMPRuntimeQuery<FJKMPNotes *> *)getNoteByIdUuid:(NSString *)uuid __attribute__((swift_name("getNoteById(uuid:)")));
- (FJKMPRuntimeQuery<id> *)getNoteByIdUuid:(NSString *)uuid mapper:(id (^)(NSString *, NSString *, NSString *, FJKMPBoolean *, NSString *, NSString *))mapper __attribute__((swift_name("getNoteById(uuid:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPNotes *> *)getNotesUserId:(NSString *)userId __attribute__((swift_name("getNotes(userId:)")));
- (FJKMPRuntimeQuery<id> *)getNotesUserId:(NSString *)userId mapper:(id (^)(NSString *, NSString *, NSString *, FJKMPBoolean *, NSString *, NSString *))mapper __attribute__((swift_name("getNotes(userId:mapper:)")));
- (void)updateNoteText:(NSString *)text isPinned:(BOOL)isPinned updatedDate:(NSString *)updatedDate uuid:(NSString *)uuid __attribute__((swift_name("updateNote(text:isPinned:updatedDate:uuid:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PhotoMeasurements")))
@interface FJKMPPhotoMeasurements : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(NSString *)date createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("init(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPPhotoMeasurements *)doCopyUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(NSString *)date createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("doCopy(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) NSString *date __attribute__((swift_name("date")));
@property (readonly) NSString *diaryId __attribute__((swift_name("diaryId")));
@property (readonly) NSString *path __attribute__((swift_name("path")));
@property (readonly) NSString *type __attribute__((swift_name("type")));
@property (readonly) NSString *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *url __attribute__((swift_name("url")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PhotoMeasurementsQueries")))
@interface FJKMPPhotoMeasurementsQueries : FJKMPRuntimeTransacterImpl
- (instancetype)initWithDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("init(driver:)"))) __attribute__((objc_designated_initializer));
- (void)createPhotoMeasurementUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(NSString *)date createdDate:(NSString *)createdDate updatedDate:(NSString *)updatedDate __attribute__((swift_name("createPhotoMeasurement(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)")));
- (void)deletePhotoMeasurementUuid:(NSString *)uuid __attribute__((swift_name("deletePhotoMeasurement(uuid:)")));
- (void)deletePhotoMeasurements __attribute__((swift_name("deletePhotoMeasurements()")));
- (void)deletePhotoMeasurementsByDiaryIdDiaryId:(NSString *)diaryId __attribute__((swift_name("deletePhotoMeasurementsByDiaryId(diaryId:)")));
- (void)deletePhotoMeasurementsByUserIdUserId:(NSString *)userId __attribute__((swift_name("deletePhotoMeasurementsByUserId(userId:)")));
- (FJKMPRuntimeQuery<FJKMPPhotoMeasurements *> *)getPhotoMeasurementByIdUuid:(NSString *)uuid __attribute__((swift_name("getPhotoMeasurementById(uuid:)")));
- (FJKMPRuntimeQuery<id> *)getPhotoMeasurementByIdUuid:(NSString *)uuid mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *))mapper __attribute__((swift_name("getPhotoMeasurementById(uuid:mapper:)")));
- (FJKMPRuntimeQuery<FJKMPPhotoMeasurements *> *)getPhotoMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getPhotoMeasurements(userId:diaryId:)")));
- (FJKMPRuntimeQuery<id> *)getPhotoMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId mapper:(id (^)(NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *, NSString *))mapper __attribute__((swift_name("getPhotoMeasurements(userId:diaryId:mapper:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("CategoriesDBDataSource")))
@interface FJKMPCategoriesDBDataSource : FJKMPBase
- (instancetype)initWithDao:(FJKMPCategoryQueries *)dao __attribute__((swift_name("init(dao:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBCategoryObject *)createCategoryUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int32_t)type details:(NSString * _Nullable)details __attribute__((swift_name("createCategory(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)")));
- (void)deleteAllCategories __attribute__((swift_name("deleteAllCategories()")));
- (NSArray<FJKMPDBCategoryObject *> *)getAllCategories __attribute__((swift_name("getAllCategories()")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getAllCategoriesFlow __attribute__((swift_name("getAllCategoriesFlow()")));
- (NSArray<FJKMPDBCategoryObject *> *)getCategoriesByUuidsUuids:(NSArray<NSString *> *)uuids __attribute__((swift_name("getCategoriesByUuids(uuids:)")));
- (FJKMPDBCategoryObject *)getCategoryByRemoteIdRemoteId:(NSString *)remoteId __attribute__((swift_name("getCategoryByRemoteId(remoteId:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getCategoryByRemoteIdFlowRemoteId:(NSString *)remoteId __attribute__((swift_name("getCategoryByRemoteIdFlow(remoteId:)")));
- (FJKMPDBCategoryObject *)getCategoryByUuidUuid:(NSString *)uuid __attribute__((swift_name("getCategoryByUuid(uuid:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getCategoryByUuidFlowUuid:(NSString *)uuid __attribute__((swift_name("getCategoryByUuidFlow(uuid:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ExercisesDBDataSource")))
@interface FJKMPExercisesDBDataSource : FJKMPBase
- (instancetype)initWithDao:(FJKMPExercisesQueries *)dao mapper:(FJKMPExerciseDBMapper *)mapper __attribute__((swift_name("init(dao:mapper:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBExerciseObject *)createExerciseUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk details:(NSString * _Nullable)details image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 categoryUuid:(NSString *)categoryUuid secondaryCategoryUuids:(NSArray<NSString *> * _Nullable)secondaryCategoryUuids resultType:(int32_t)resultType isGlobal:(BOOL)isGlobal __attribute__((swift_name("createExercise(uuid:remoteId:nameEn:nameRu:nameUk:details:image1:image2:categoryUuid:secondaryCategoryUuids:resultType:isGlobal:)")));
- (void)deleteAllExercises __attribute__((swift_name("deleteAllExercises()")));
- (void)deleteExerciseUuid:(NSString *)uuid __attribute__((swift_name("deleteExercise(uuid:)")));
- (NSArray<FJKMPDBExerciseObject *> *)getAllExercises __attribute__((swift_name("getAllExercises()")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getAllExercisesFlow __attribute__((swift_name("getAllExercisesFlow()")));
- (FJKMPDBExerciseObject *)getExerciseByRemoteIdRemoteId:(NSString *)remoteId __attribute__((swift_name("getExerciseByRemoteId(remoteId:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getExerciseByRemoteIdFlowRemoteId:(NSString *)remoteId __attribute__((swift_name("getExerciseByRemoteIdFlow(remoteId:)")));
- (FJKMPDBExerciseObject *)getExerciseByUuidUuid:(NSString *)uuid __attribute__((swift_name("getExerciseByUuid(uuid:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getExerciseByUuidFlowUuid:(NSString *)uuid __attribute__((swift_name("getExerciseByUuidFlow(uuid:)")));
- (NSArray<FJKMPDBExerciseObject *> *)getExercisesByCategoryUuidCategoryUuid:(NSString *)categoryUuid __attribute__((swift_name("getExercisesByCategoryUuid(categoryUuid:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getExercisesByCategoryUuidFlowCategoryUuid:(NSString *)categoryUuid __attribute__((swift_name("getExercisesByCategoryUuidFlow(categoryUuid:)")));
- (FJKMPDBExerciseObject *)updateExerciseUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk details:(NSString * _Nullable)details categoryUuid:(NSString *)categoryUuid secondaryCategoryUuids:(NSArray<NSString *> * _Nullable)secondaryCategoryUuids resultType:(int32_t)resultType __attribute__((swift_name("updateExercise(uuid:remoteId:nameEn:nameRu:nameUk:details:categoryUuid:secondaryCategoryUuids:resultType:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DBCategoryObject")))
@interface FJKMPDBCategoryObject : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int32_t)type details:(NSString * _Nullable)details __attribute__((swift_name("init(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBCategoryObject *)doCopyUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString *)nameUk type:(int32_t)type details:(NSString * _Nullable)details __attribute__((swift_name("doCopy(uuid:remoteId:nameEn:nameRu:nameUk:type:details:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable details __attribute__((swift_name("details")));
@property (readonly) NSString *nameEn __attribute__((swift_name("nameEn")));
@property (readonly) NSString *nameRu __attribute__((swift_name("nameRu")));
@property (readonly) NSString *nameUk __attribute__((swift_name("nameUk")));
@property (readonly) NSString *remoteId __attribute__((swift_name("remoteId")));
@property (readonly) int32_t type __attribute__((swift_name("type")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DBExerciseObject")))
@interface FJKMPDBExerciseObject : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 details:(NSString * _Nullable)details resultType:(int32_t)resultType primaryCategory:(FJKMPDBCategoryObject *)primaryCategory secondaryCategories:(NSArray<FJKMPDBCategoryObject *> * _Nullable)secondaryCategories isGlobal:(BOOL)isGlobal __attribute__((swift_name("init(uuid:remoteId:nameEn:nameRu:nameUk:image1:image2:details:resultType:primaryCategory:secondaryCategories:isGlobal:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBExerciseObject *)doCopyUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 details:(NSString * _Nullable)details resultType:(int32_t)resultType primaryCategory:(FJKMPDBCategoryObject *)primaryCategory secondaryCategories:(NSArray<FJKMPDBCategoryObject *> * _Nullable)secondaryCategories isGlobal:(BOOL)isGlobal __attribute__((swift_name("doCopy(uuid:remoteId:nameEn:nameRu:nameUk:image1:image2:details:resultType:primaryCategory:secondaryCategories:isGlobal:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable details __attribute__((swift_name("details")));
@property (readonly) NSString * _Nullable image1 __attribute__((swift_name("image1")));
@property (readonly) NSString * _Nullable image2 __attribute__((swift_name("image2")));
@property (readonly) BOOL isGlobal __attribute__((swift_name("isGlobal")));
@property (readonly) NSString *nameEn __attribute__((swift_name("nameEn")));
@property (readonly) NSString *nameRu __attribute__((swift_name("nameRu")));
@property (readonly) NSString * _Nullable nameUk __attribute__((swift_name("nameUk")));
@property (readonly) FJKMPDBCategoryObject *primaryCategory __attribute__((swift_name("primaryCategory")));
@property (readonly) NSString *remoteId __attribute__((swift_name("remoteId")));
@property (readonly) int32_t resultType __attribute__((swift_name("resultType")));
@property (readonly) NSArray<FJKMPDBCategoryObject *> * _Nullable secondaryCategories __attribute__((swift_name("secondaryCategories")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("ExerciseDBMapper")))
@interface FJKMPExerciseDBMapper : FJKMPBase
- (instancetype)initWithCategoryDataSource:(FJKMPCategoriesDBDataSource *)categoryDataSource __attribute__((swift_name("init(categoryDataSource:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBExerciseObject *)mapUuid:(NSString *)uuid remoteId:(NSString *)remoteId nameEn:(NSString *)nameEn nameRu:(NSString *)nameRu nameUk:(NSString * _Nullable)nameUk details:(NSString * _Nullable)details image1:(NSString * _Nullable)image1 image2:(NSString * _Nullable)image2 resultType:(int32_t)resultType primaryCategoryUuid:(NSString *)primaryCategoryUuid secondaryCategoryUuids:(NSString * _Nullable)secondaryCategoryUuids isGlobal:(BOOL)isGlobal __attribute__((swift_name("map(uuid:remoteId:nameEn:nameRu:nameUk:details:image1:image2:resultType:primaryCategoryUuid:secondaryCategoryUuids:isGlobal:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("BodyMeasurementsDBDataSource")))
@interface FJKMPBodyMeasurementsDBDataSource : FJKMPBase
- (instancetype)initWithDao:(FJKMPBodyMeasurementsQueries *)dao __attribute__((swift_name("init(dao:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBBodyMeasurementObject *)createBodyMeasurementUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value:(double)value comment:(NSString * _Nullable)comment measurementDate:(FJKMPKotlinx_datetimeLocalDateTime *)measurementDate createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("createBodyMeasurement(uuid:remoteId:userId:diaryId:type:value:comment:measurementDate:createdDate:updatedDate:)")));
- (void)deleteAllBodyMeasurements __attribute__((swift_name("deleteAllBodyMeasurements()")));
- (void)deleteBodyMeasurementUuid:(NSString *)uuid __attribute__((swift_name("deleteBodyMeasurement(uuid:)")));
- (void)deleteBodyMeasurementsByDiaryIdDiaryId:(NSString *)diaryId __attribute__((swift_name("deleteBodyMeasurementsByDiaryId(diaryId:)")));
- (void)deleteBodyMeasurementsByUserIdUserId:(NSString *)userId __attribute__((swift_name("deleteBodyMeasurementsByUserId(userId:)")));
- (FJKMPDBBodyMeasurementObject *)getBodyMeasurementByIdUuid:(NSString *)uuid __attribute__((swift_name("getBodyMeasurementById(uuid:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getBodyMeasurementByIdFlowUuid:(NSString *)uuid __attribute__((swift_name("getBodyMeasurementByIdFlow(uuid:)")));
- (NSArray<FJKMPDBBodyMeasurementObject *> *)getBodyMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getBodyMeasurements(userId:diaryId:)")));
- (NSArray<FJKMPDBBodyMeasurementObject *> *)getBodyMeasurementsByTypeUserId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type __attribute__((swift_name("getBodyMeasurementsByType(userId:diaryId:type:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getBodyMeasurementsByTypeFlowUserId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type __attribute__((swift_name("getBodyMeasurementsByTypeFlow(userId:diaryId:type:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getBodyMeasurementsFlowUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getBodyMeasurementsFlow(userId:diaryId:)")));
- (FJKMPDBBodyMeasurementObject *)updateBodyMeasurementUuid:(NSString *)uuid value:(double)value comment:(NSString * _Nullable)comment measurementDate:(FJKMPKotlinx_datetimeLocalDateTime *)measurementDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("updateBodyMeasurement(uuid:value:comment:measurementDate:updatedDate:)")));
- (FJKMPDBBodyMeasurementObject *)updateBodyMeasurementRemoteIdUuid:(NSString *)uuid remoteId:(NSString *)remoteId __attribute__((swift_name("updateBodyMeasurementRemoteId(uuid:remoteId:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("PhotoMeasurementsDBDataSource")))
@interface FJKMPPhotoMeasurementsDBDataSource : FJKMPBase
- (instancetype)initWithDao:(FJKMPPhotoMeasurementsQueries *)dao __attribute__((swift_name("init(dao:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBPhotoMeasurementObject *)createPhotoMeasurementUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(FJKMPKotlinx_datetimeLocalDate *)date createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("createPhotoMeasurement(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)")));
- (void)deletePhotoMeasurementUuid:(NSString *)uuid __attribute__((swift_name("deletePhotoMeasurement(uuid:)")));
- (void)deletePhotoMeasurements __attribute__((swift_name("deletePhotoMeasurements()")));
- (void)deletePhotoMeasurementsByDiaryIdDiaryId:(NSString *)diaryId __attribute__((swift_name("deletePhotoMeasurementsByDiaryId(diaryId:)")));
- (void)deletePhotoMeasurementsByUserIdUserId:(NSString *)userId __attribute__((swift_name("deletePhotoMeasurementsByUserId(userId:)")));
- (FJKMPDBPhotoMeasurementObject *)getPhotoMeasurementByIdUuid:(NSString *)uuid __attribute__((swift_name("getPhotoMeasurementById(uuid:)")));
- (NSArray<FJKMPDBPhotoMeasurementObject *> *)getPhotoMeasurementsUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getPhotoMeasurements(userId:diaryId:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getPhotoMeasurementsFlowUserId:(NSString *)userId diaryId:(NSString *)diaryId __attribute__((swift_name("getPhotoMeasurementsFlow(userId:diaryId:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DBBodyMeasurementObject")))
@interface FJKMPDBBodyMeasurementObject : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value:(double)value comment:(NSString * _Nullable)comment measurementDate:(FJKMPKotlinx_datetimeLocalDateTime *)measurementDate createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("init(uuid:remoteId:userId:diaryId:type:value:comment:measurementDate:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBBodyMeasurementObject *)doCopyUuid:(NSString *)uuid remoteId:(NSString * _Nullable)remoteId userId:(NSString *)userId diaryId:(NSString *)diaryId type:(NSString *)type value:(double)value comment:(NSString * _Nullable)comment measurementDate:(FJKMPKotlinx_datetimeLocalDateTime *)measurementDate createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("doCopy(uuid:remoteId:userId:diaryId:type:value:comment:measurementDate:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString * _Nullable comment __attribute__((swift_name("comment")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) NSString *diaryId __attribute__((swift_name("diaryId")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *measurementDate __attribute__((swift_name("measurementDate")));
@property (readonly) NSString * _Nullable remoteId __attribute__((swift_name("remoteId")));
@property (readonly) NSString *type __attribute__((swift_name("type")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@property (readonly) double value __attribute__((swift_name("value")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DBPhotoMeasurementObject")))
@interface FJKMPDBPhotoMeasurementObject : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(FJKMPKotlinx_datetimeLocalDate *)date createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("init(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBPhotoMeasurementObject *)doCopyUuid:(NSString *)uuid userId:(NSString *)userId diaryId:(NSString *)diaryId path:(NSString *)path url:(NSString *)url type:(NSString *)type date:(FJKMPKotlinx_datetimeLocalDate *)date createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("doCopy(uuid:userId:diaryId:path:url:type:date:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) FJKMPKotlinx_datetimeLocalDate *date __attribute__((swift_name("date")));
@property (readonly) NSString *diaryId __attribute__((swift_name("diaryId")));
@property (readonly) NSString *path __attribute__((swift_name("path")));
@property (readonly) NSString *type __attribute__((swift_name("type")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *url __attribute__((swift_name("url")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("NotesDBDataSource")))
@interface FJKMPNotesDBDataSource : FJKMPBase
- (instancetype)initWithDao:(FJKMPNotesQueries *)dao __attribute__((swift_name("init(dao:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBNoteObject *)createNoteUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("createNote(uuid:userId:text:isPinned:createdDate:updatedDate:)")));
- (void)deleteAllNotes __attribute__((swift_name("deleteAllNotes()")));
- (void)deleteNoteUuid:(NSString *)uuid __attribute__((swift_name("deleteNote(uuid:)")));
- (void)deleteUserNotesUserId:(NSString *)userId __attribute__((swift_name("deleteUserNotes(userId:)")));
- (FJKMPDBNoteObject *)getNoteByIdUuid:(NSString *)uuid __attribute__((swift_name("getNoteById(uuid:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getNoteByIdFlowUuid:(NSString *)uuid __attribute__((swift_name("getNoteByIdFlow(uuid:)")));
- (NSArray<FJKMPDBNoteObject *> *)getNotesUserId:(NSString *)userId __attribute__((swift_name("getNotes(userId:)")));
- (id<FJKMPKotlinx_coroutines_coreFlow>)getNotesFlowUserId:(NSString *)userId __attribute__((swift_name("getNotesFlow(userId:)")));
- (FJKMPDBNoteObject *)updateNoteUuid:(NSString *)uuid text:(NSString *)text isPinned:(BOOL)isPinned updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("updateNote(uuid:text:isPinned:updatedDate:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DBNoteObject")))
@interface FJKMPDBNoteObject : FJKMPBase
- (instancetype)initWithUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("init(uuid:userId:text:isPinned:createdDate:updatedDate:)"))) __attribute__((objc_designated_initializer));
- (FJKMPDBNoteObject *)doCopyUuid:(NSString *)uuid userId:(NSString *)userId text:(NSString *)text isPinned:(BOOL)isPinned createdDate:(FJKMPKotlinx_datetimeLocalDateTime *)createdDate updatedDate:(FJKMPKotlinx_datetimeLocalDateTime *)updatedDate __attribute__((swift_name("doCopy(uuid:userId:text:isPinned:createdDate:updatedDate:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *createdDate __attribute__((swift_name("createdDate")));
@property (readonly) BOOL isPinned __attribute__((swift_name("isPinned")));
@property (readonly) NSString *text __attribute__((swift_name("text")));
@property (readonly) FJKMPKotlinx_datetimeLocalDateTime *updatedDate __attribute__((swift_name("updatedDate")));
@property (readonly) NSString *userId __attribute__((swift_name("userId")));
@property (readonly) NSString *uuid __attribute__((swift_name("uuid")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("DatabaseDriverFactory")))
@interface FJKMPDatabaseDriverFactory : FJKMPBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (id<FJKMPRuntimeSqlDriver>)createDriver __attribute__((swift_name("createDriver()")));
@end

__attribute__((swift_name("RuntimeCloseable")))
@protocol FJKMPRuntimeCloseable
@required
- (void)close __attribute__((swift_name("close()")));
@end

__attribute__((swift_name("RuntimeSqlDriver")))
@protocol FJKMPRuntimeSqlDriver <FJKMPRuntimeCloseable>
@required
- (void)addListenerQueryKeys:(FJKMPKotlinArray<NSString *> *)queryKeys listener:(id<FJKMPRuntimeQueryListener>)listener __attribute__((swift_name("addListener(queryKeys:listener:)")));
- (FJKMPRuntimeTransacterTransaction * _Nullable)currentTransaction __attribute__((swift_name("currentTransaction()")));
- (id<FJKMPRuntimeQueryResult>)executeIdentifier:(FJKMPInt * _Nullable)identifier sql:(NSString *)sql parameters:(int32_t)parameters binders:(void (^ _Nullable)(id<FJKMPRuntimeSqlPreparedStatement>))binders __attribute__((swift_name("execute(identifier:sql:parameters:binders:)")));
- (id<FJKMPRuntimeQueryResult>)executeQueryIdentifier:(FJKMPInt * _Nullable)identifier sql:(NSString *)sql mapper:(id<FJKMPRuntimeQueryResult> (^)(id<FJKMPRuntimeSqlCursor>))mapper parameters:(int32_t)parameters binders:(void (^ _Nullable)(id<FJKMPRuntimeSqlPreparedStatement>))binders __attribute__((swift_name("executeQuery(identifier:sql:mapper:parameters:binders:)")));
- (id<FJKMPRuntimeQueryResult>)doNewTransaction __attribute__((swift_name("doNewTransaction()")));
- (void)notifyListenersQueryKeys:(FJKMPKotlinArray<NSString *> *)queryKeys __attribute__((swift_name("notifyListeners(queryKeys:)")));
- (void)removeListenerQueryKeys:(FJKMPKotlinArray<NSString *> *)queryKeys listener:(id<FJKMPRuntimeQueryListener>)listener __attribute__((swift_name("removeListener(queryKeys:listener:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinUnit")))
@interface FJKMPKotlinUnit : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)unit __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinUnit *shared __attribute__((swift_name("shared")));
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((swift_name("RuntimeTransactionCallbacks")))
@protocol FJKMPRuntimeTransactionCallbacks
@required
- (void)afterCommitFunction:(void (^)(void))function __attribute__((swift_name("afterCommit(function:)")));
- (void)afterRollbackFunction:(void (^)(void))function __attribute__((swift_name("afterRollback(function:)")));
@end

__attribute__((swift_name("RuntimeTransacterTransaction")))
@interface FJKMPRuntimeTransacterTransaction : FJKMPBase <FJKMPRuntimeTransactionCallbacks>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (void)afterCommitFunction:(void (^)(void))function __attribute__((swift_name("afterCommit(function:)")));
- (void)afterRollbackFunction:(void (^)(void))function __attribute__((swift_name("afterRollback(function:)")));

/**
 * @note This method has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
- (id<FJKMPRuntimeQueryResult>)endTransactionSuccessful:(BOOL)successful __attribute__((swift_name("endTransaction(successful:)")));

/**
 * @note This property has protected visibility in Kotlin source and is intended only for use by subclasses.
*/
@property (readonly) FJKMPRuntimeTransacterTransaction * _Nullable enclosingTransaction __attribute__((swift_name("enclosingTransaction")));
@end

__attribute__((swift_name("KotlinThrowable")))
@interface FJKMPKotlinThrowable : FJKMPBase
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));

/**
 * @note annotations
 *   kotlin.experimental.ExperimentalNativeApi
*/
- (FJKMPKotlinArray<NSString *> *)getStackTrace __attribute__((swift_name("getStackTrace()")));
- (void)printStackTrace __attribute__((swift_name("printStackTrace()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) FJKMPKotlinThrowable * _Nullable cause __attribute__((swift_name("cause")));
@property (readonly) NSString * _Nullable message __attribute__((swift_name("message")));
- (NSError *)asError __attribute__((swift_name("asError()")));
@end

__attribute__((swift_name("RuntimeTransactionWithoutReturn")))
@protocol FJKMPRuntimeTransactionWithoutReturn <FJKMPRuntimeTransactionCallbacks>
@required
- (void)rollback __attribute__((swift_name("rollback()")));
- (void)transactionBody:(void (^)(id<FJKMPRuntimeTransactionWithoutReturn>))body __attribute__((swift_name("transaction(body:)")));
@end

__attribute__((swift_name("RuntimeTransactionWithReturn")))
@protocol FJKMPRuntimeTransactionWithReturn <FJKMPRuntimeTransactionCallbacks>
@required
- (void)rollbackReturnValue:(id _Nullable)returnValue __attribute__((swift_name("rollback(returnValue:)")));
- (id _Nullable)transactionBody_:(id _Nullable (^)(id<FJKMPRuntimeTransactionWithReturn>))body __attribute__((swift_name("transaction(body_:)")));
@end

__attribute__((swift_name("RuntimeExecutableQuery")))
@interface FJKMPRuntimeExecutableQuery<__covariant RowType> : FJKMPBase
- (instancetype)initWithMapper:(RowType (^)(id<FJKMPRuntimeSqlCursor>))mapper __attribute__((swift_name("init(mapper:)"))) __attribute__((objc_designated_initializer));
- (id<FJKMPRuntimeQueryResult>)executeMapper:(id<FJKMPRuntimeQueryResult> (^)(id<FJKMPRuntimeSqlCursor>))mapper __attribute__((swift_name("execute(mapper:)")));
- (NSArray<RowType> *)executeAsList __attribute__((swift_name("executeAsList()")));
- (RowType)executeAsOne __attribute__((swift_name("executeAsOne()")));
- (RowType _Nullable)executeAsOneOrNull __attribute__((swift_name("executeAsOneOrNull()")));
@property (readonly) RowType (^mapper)(id<FJKMPRuntimeSqlCursor>) __attribute__((swift_name("mapper")));
@end

__attribute__((swift_name("RuntimeQuery")))
@interface FJKMPRuntimeQuery<__covariant RowType> : FJKMPRuntimeExecutableQuery<RowType>
- (instancetype)initWithMapper:(RowType (^)(id<FJKMPRuntimeSqlCursor>))mapper __attribute__((swift_name("init(mapper:)"))) __attribute__((objc_designated_initializer));
- (void)addListenerListener:(id<FJKMPRuntimeQueryListener>)listener __attribute__((swift_name("addListener(listener:)")));
- (void)removeListenerListener:(id<FJKMPRuntimeQueryListener>)listener __attribute__((swift_name("removeListener(listener:)")));
@end

__attribute__((swift_name("RuntimeSqlSchema")))
@protocol FJKMPRuntimeSqlSchema
@required
- (id<FJKMPRuntimeQueryResult>)createDriver:(id<FJKMPRuntimeSqlDriver>)driver __attribute__((swift_name("create(driver:)")));
- (id<FJKMPRuntimeQueryResult>)migrateDriver:(id<FJKMPRuntimeSqlDriver>)driver oldVersion:(int64_t)oldVersion newVersion:(int64_t)newVersion callbacks:(FJKMPKotlinArray<FJKMPRuntimeAfterVersion *> *)callbacks __attribute__((swift_name("migrate(driver:oldVersion:newVersion:callbacks:)")));
@property (readonly) int64_t version __attribute__((swift_name("version")));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlow")))
@protocol FJKMPKotlinx_coroutines_coreFlow
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)collectCollector:(id<FJKMPKotlinx_coroutines_coreFlowCollector>)collector completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("collect(collector:completionHandler:)")));
@end

__attribute__((swift_name("KotlinComparable")))
@protocol FJKMPKotlinComparable
@required
- (int32_t)compareToOther:(id _Nullable)other __attribute__((swift_name("compareTo(other:)")));
@end


/**
 * @note annotations
 *   kotlinx.serialization.Serializable(with=NormalClass(value=kotlinx/datetime/serializers/LocalDateTimeIso8601Serializer))
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalDateTime")))
@interface FJKMPKotlinx_datetimeLocalDateTime : FJKMPBase <FJKMPKotlinComparable>
- (instancetype)initWithDate:(FJKMPKotlinx_datetimeLocalDate *)date time:(FJKMPKotlinx_datetimeLocalTime *)time __attribute__((swift_name("init(date:time:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithYear:(int32_t)year monthNumber:(int32_t)monthNumber dayOfMonth:(int32_t)dayOfMonth hour:(int32_t)hour minute:(int32_t)minute second:(int32_t)second nanosecond:(int32_t)nanosecond __attribute__((swift_name("init(year:monthNumber:dayOfMonth:hour:minute:second:nanosecond:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithYear:(int32_t)year month:(FJKMPKotlinx_datetimeMonth *)month dayOfMonth:(int32_t)dayOfMonth hour:(int32_t)hour minute:(int32_t)minute second:(int32_t)second nanosecond:(int32_t)nanosecond __attribute__((swift_name("init(year:month:dayOfMonth:hour:minute:second:nanosecond:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinx_datetimeLocalDateTimeCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(FJKMPKotlinx_datetimeLocalDateTime *)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) FJKMPKotlinx_datetimeLocalDate *date __attribute__((swift_name("date")));
@property (readonly) int32_t dayOfMonth __attribute__((swift_name("dayOfMonth")));
@property (readonly) FJKMPKotlinx_datetimeDayOfWeek *dayOfWeek __attribute__((swift_name("dayOfWeek")));
@property (readonly) int32_t dayOfYear __attribute__((swift_name("dayOfYear")));
@property (readonly) int32_t hour __attribute__((swift_name("hour")));
@property (readonly) int32_t minute __attribute__((swift_name("minute")));
@property (readonly) FJKMPKotlinx_datetimeMonth *month __attribute__((swift_name("month")));
@property (readonly) int32_t monthNumber __attribute__((swift_name("monthNumber")));
@property (readonly) int32_t nanosecond __attribute__((swift_name("nanosecond")));
@property (readonly) int32_t second __attribute__((swift_name("second")));
@property (readonly) FJKMPKotlinx_datetimeLocalTime *time __attribute__((swift_name("time")));
@property (readonly) int32_t year __attribute__((swift_name("year")));
@end


/**
 * @note annotations
 *   kotlinx.serialization.Serializable(with=NormalClass(value=kotlinx/datetime/serializers/LocalDateIso8601Serializer))
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalDate")))
@interface FJKMPKotlinx_datetimeLocalDate : FJKMPBase <FJKMPKotlinComparable>
- (instancetype)initWithYear:(int32_t)year monthNumber:(int32_t)monthNumber dayOfMonth:(int32_t)dayOfMonth __attribute__((swift_name("init(year:monthNumber:dayOfMonth:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithYear:(int32_t)year month:(FJKMPKotlinx_datetimeMonth *)month dayOfMonth:(int32_t)dayOfMonth __attribute__((swift_name("init(year:month:dayOfMonth:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinx_datetimeLocalDateCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(FJKMPKotlinx_datetimeLocalDate *)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (int32_t)toEpochDays __attribute__((swift_name("toEpochDays()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) int32_t dayOfMonth __attribute__((swift_name("dayOfMonth")));
@property (readonly) FJKMPKotlinx_datetimeDayOfWeek *dayOfWeek __attribute__((swift_name("dayOfWeek")));
@property (readonly) int32_t dayOfYear __attribute__((swift_name("dayOfYear")));
@property (readonly) FJKMPKotlinx_datetimeMonth *month __attribute__((swift_name("month")));
@property (readonly) int32_t monthNumber __attribute__((swift_name("monthNumber")));
@property (readonly) int32_t year __attribute__((swift_name("year")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinArray")))
@interface FJKMPKotlinArray<T> : FJKMPBase
+ (instancetype)arrayWithSize:(int32_t)size init:(T _Nullable (^)(FJKMPInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (T _Nullable)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (id<FJKMPKotlinIterator>)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(T _Nullable)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((swift_name("RuntimeQueryListener")))
@protocol FJKMPRuntimeQueryListener
@required
- (void)queryResultsChanged __attribute__((swift_name("queryResultsChanged()")));
@end

__attribute__((swift_name("RuntimeQueryResult")))
@protocol FJKMPRuntimeQueryResult
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)awaitWithCompletionHandler:(void (^)(id _Nullable_result, NSError * _Nullable))completionHandler __attribute__((swift_name("await(completionHandler:)")));
@property (readonly) id _Nullable value __attribute__((swift_name("value")));
@end

__attribute__((swift_name("RuntimeSqlPreparedStatement")))
@protocol FJKMPRuntimeSqlPreparedStatement
@required
- (void)bindBooleanIndex:(int32_t)index boolean:(FJKMPBoolean * _Nullable)boolean __attribute__((swift_name("bindBoolean(index:boolean:)")));
- (void)bindBytesIndex:(int32_t)index bytes:(FJKMPKotlinByteArray * _Nullable)bytes __attribute__((swift_name("bindBytes(index:bytes:)")));
- (void)bindDoubleIndex:(int32_t)index double:(FJKMPDouble * _Nullable)double_ __attribute__((swift_name("bindDouble(index:double:)")));
- (void)bindLongIndex:(int32_t)index long:(FJKMPLong * _Nullable)long_ __attribute__((swift_name("bindLong(index:long:)")));
- (void)bindStringIndex:(int32_t)index string:(NSString * _Nullable)string __attribute__((swift_name("bindString(index:string:)")));
@end

__attribute__((swift_name("RuntimeSqlCursor")))
@protocol FJKMPRuntimeSqlCursor
@required
- (FJKMPBoolean * _Nullable)getBooleanIndex:(int32_t)index __attribute__((swift_name("getBoolean(index:)")));
- (FJKMPKotlinByteArray * _Nullable)getBytesIndex:(int32_t)index __attribute__((swift_name("getBytes(index:)")));
- (FJKMPDouble * _Nullable)getDoubleIndex:(int32_t)index __attribute__((swift_name("getDouble(index:)")));
- (FJKMPLong * _Nullable)getLongIndex:(int32_t)index __attribute__((swift_name("getLong(index:)")));
- (NSString * _Nullable)getStringIndex:(int32_t)index __attribute__((swift_name("getString(index:)")));
- (id<FJKMPRuntimeQueryResult>)next __attribute__((swift_name("next()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("RuntimeAfterVersion")))
@interface FJKMPRuntimeAfterVersion : FJKMPBase
- (instancetype)initWithAfterVersion:(int64_t)afterVersion block:(void (^)(id<FJKMPRuntimeSqlDriver>))block __attribute__((swift_name("init(afterVersion:block:)"))) __attribute__((objc_designated_initializer));
@property (readonly) int64_t afterVersion __attribute__((swift_name("afterVersion")));
@property (readonly) void (^block)(id<FJKMPRuntimeSqlDriver>) __attribute__((swift_name("block")));
@end

__attribute__((swift_name("KotlinException")))
@interface FJKMPKotlinException : FJKMPKotlinThrowable
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinRuntimeException")))
@interface FJKMPKotlinRuntimeException : FJKMPKotlinException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("KotlinIllegalStateException")))
@interface FJKMPKotlinIllegalStateException : FJKMPKotlinRuntimeException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.4")
*/
__attribute__((swift_name("KotlinCancellationException")))
@interface FJKMPKotlinCancellationException : FJKMPKotlinIllegalStateException
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (instancetype)initWithMessage:(NSString * _Nullable)message __attribute__((swift_name("init(message:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithCause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(cause:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMessage:(NSString * _Nullable)message cause:(FJKMPKotlinThrowable * _Nullable)cause __attribute__((swift_name("init(message:cause:)"))) __attribute__((objc_designated_initializer));
@end

__attribute__((swift_name("Kotlinx_coroutines_coreFlowCollector")))
@protocol FJKMPKotlinx_coroutines_coreFlowCollector
@required

/**
 * @note This method converts instances of CancellationException to errors.
 * Other uncaught Kotlin exceptions are fatal.
*/
- (void)emitValue:(id _Nullable)value completionHandler:(void (^)(NSError * _Nullable))completionHandler __attribute__((swift_name("emit(value:completionHandler:)")));
@end


/**
 * @note annotations
 *   kotlinx.serialization.Serializable(with=NormalClass(value=kotlinx/datetime/serializers/LocalTimeIso8601Serializer))
*/
__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalTime")))
@interface FJKMPKotlinx_datetimeLocalTime : FJKMPBase <FJKMPKotlinComparable>
- (instancetype)initWithHour:(int32_t)hour minute:(int32_t)minute second:(int32_t)second nanosecond:(int32_t)nanosecond __attribute__((swift_name("init(hour:minute:second:nanosecond:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinx_datetimeLocalTimeCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(FJKMPKotlinx_datetimeLocalTime *)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (int32_t)toMillisecondOfDay __attribute__((swift_name("toMillisecondOfDay()")));
- (int64_t)toNanosecondOfDay __attribute__((swift_name("toNanosecondOfDay()")));
- (int32_t)toSecondOfDay __attribute__((swift_name("toSecondOfDay()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) int32_t hour __attribute__((swift_name("hour")));
@property (readonly) int32_t minute __attribute__((swift_name("minute")));
@property (readonly) int32_t nanosecond __attribute__((swift_name("nanosecond")));
@property (readonly) int32_t second __attribute__((swift_name("second")));
@end

__attribute__((swift_name("KotlinEnum")))
@interface FJKMPKotlinEnum<E> : FJKMPBase <FJKMPKotlinComparable>
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinEnumCompanion *companion __attribute__((swift_name("companion")));
- (int32_t)compareToOther:(E)other __attribute__((swift_name("compareTo(other:)")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSString *name __attribute__((swift_name("name")));
@property (readonly) int32_t ordinal __attribute__((swift_name("ordinal")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeMonth")))
@interface FJKMPKotlinx_datetimeMonth : FJKMPKotlinEnum<FJKMPKotlinx_datetimeMonth *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *january __attribute__((swift_name("january")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *february __attribute__((swift_name("february")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *march __attribute__((swift_name("march")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *april __attribute__((swift_name("april")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *may __attribute__((swift_name("may")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *june __attribute__((swift_name("june")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *july __attribute__((swift_name("july")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *august __attribute__((swift_name("august")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *september __attribute__((swift_name("september")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *october __attribute__((swift_name("october")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *november __attribute__((swift_name("november")));
@property (class, readonly) FJKMPKotlinx_datetimeMonth *december __attribute__((swift_name("december")));
+ (FJKMPKotlinArray<FJKMPKotlinx_datetimeMonth *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<FJKMPKotlinx_datetimeMonth *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalDateTime.Companion")))
@interface FJKMPKotlinx_datetimeLocalDateTimeCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinx_datetimeLocalDateTimeCompanion *shared __attribute__((swift_name("shared")));
- (id<FJKMPKotlinx_datetimeDateTimeFormat>)FormatBuilder:(void (^)(id<FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDateTime>))builder __attribute__((swift_name("Format(builder:)")));
- (FJKMPKotlinx_datetimeLocalDateTime *)parseInput:(id)input format:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("parse(input:format:)")));
- (id<FJKMPKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeDayOfWeek")))
@interface FJKMPKotlinx_datetimeDayOfWeek : FJKMPKotlinEnum<FJKMPKotlinx_datetimeDayOfWeek *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *monday __attribute__((swift_name("monday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *tuesday __attribute__((swift_name("tuesday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *wednesday __attribute__((swift_name("wednesday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *thursday __attribute__((swift_name("thursday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *friday __attribute__((swift_name("friday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *saturday __attribute__((swift_name("saturday")));
@property (class, readonly) FJKMPKotlinx_datetimeDayOfWeek *sunday __attribute__((swift_name("sunday")));
+ (FJKMPKotlinArray<FJKMPKotlinx_datetimeDayOfWeek *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<FJKMPKotlinx_datetimeDayOfWeek *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalDate.Companion")))
@interface FJKMPKotlinx_datetimeLocalDateCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinx_datetimeLocalDateCompanion *shared __attribute__((swift_name("shared")));
- (id<FJKMPKotlinx_datetimeDateTimeFormat>)FormatBlock:(void (^)(id<FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDate>))block __attribute__((swift_name("Format(block:)")));
- (FJKMPKotlinx_datetimeLocalDate *)fromEpochDaysEpochDays:(int32_t)epochDays __attribute__((swift_name("fromEpochDays(epochDays:)")));
- (FJKMPKotlinx_datetimeLocalDate *)parseInput:(id)input format:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("parse(input:format:)")));
- (id<FJKMPKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end

__attribute__((swift_name("KotlinIterator")))
@protocol FJKMPKotlinIterator
@required
- (BOOL)hasNext __attribute__((swift_name("hasNext()")));
- (id _Nullable)next __attribute__((swift_name("next()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinByteArray")))
@interface FJKMPKotlinByteArray : FJKMPBase
+ (instancetype)arrayWithSize:(int32_t)size __attribute__((swift_name("init(size:)")));
+ (instancetype)arrayWithSize:(int32_t)size init:(FJKMPByte *(^)(FJKMPInt *))init __attribute__((swift_name("init(size:init:)")));
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (int8_t)getIndex:(int32_t)index __attribute__((swift_name("get(index:)")));
- (FJKMPKotlinByteIterator *)iterator __attribute__((swift_name("iterator()")));
- (void)setIndex:(int32_t)index value:(int8_t)value __attribute__((swift_name("set(index:value:)")));
@property (readonly) int32_t size __attribute__((swift_name("size")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeLocalTime.Companion")))
@interface FJKMPKotlinx_datetimeLocalTimeCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinx_datetimeLocalTimeCompanion *shared __attribute__((swift_name("shared")));
- (id<FJKMPKotlinx_datetimeDateTimeFormat>)FormatBuilder:(void (^)(id<FJKMPKotlinx_datetimeDateTimeFormatBuilderWithTime>))builder __attribute__((swift_name("Format(builder:)")));
- (FJKMPKotlinx_datetimeLocalTime *)fromMillisecondOfDayMillisecondOfDay:(int32_t)millisecondOfDay __attribute__((swift_name("fromMillisecondOfDay(millisecondOfDay:)")));
- (FJKMPKotlinx_datetimeLocalTime *)fromNanosecondOfDayNanosecondOfDay:(int64_t)nanosecondOfDay __attribute__((swift_name("fromNanosecondOfDay(nanosecondOfDay:)")));
- (FJKMPKotlinx_datetimeLocalTime *)fromSecondOfDaySecondOfDay:(int32_t)secondOfDay __attribute__((swift_name("fromSecondOfDay(secondOfDay:)")));
- (FJKMPKotlinx_datetimeLocalTime *)parseInput:(id)input format:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("parse(input:format:)")));
- (id<FJKMPKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("serializer()")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinEnumCompanion")))
@interface FJKMPKotlinEnumCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinEnumCompanion *shared __attribute__((swift_name("shared")));
@end

__attribute__((swift_name("Kotlinx_datetimeDateTimeFormat")))
@protocol FJKMPKotlinx_datetimeDateTimeFormat
@required
- (NSString *)formatValue:(id _Nullable)value __attribute__((swift_name("format(value:)")));
- (id<FJKMPKotlinAppendable>)formatToAppendable:(id<FJKMPKotlinAppendable>)appendable value:(id _Nullable)value __attribute__((swift_name("formatTo(appendable:value:)")));
- (id _Nullable)parseInput:(id)input __attribute__((swift_name("parse(input:)")));
- (id _Nullable)parseOrNullInput:(id)input __attribute__((swift_name("parseOrNull(input:)")));
@end

__attribute__((swift_name("Kotlinx_datetimeDateTimeFormatBuilder")))
@protocol FJKMPKotlinx_datetimeDateTimeFormatBuilder
@required
- (void)charsValue:(NSString *)value __attribute__((swift_name("chars(value:)")));
@end

__attribute__((swift_name("Kotlinx_datetimeDateTimeFormatBuilderWithDate")))
@protocol FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDate <FJKMPKotlinx_datetimeDateTimeFormatBuilder>
@required
- (void)dateFormat:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("date(format:)")));
- (void)dayOfMonthPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("dayOfMonth(padding:)")));
- (void)dayOfWeekNames:(FJKMPKotlinx_datetimeDayOfWeekNames *)names __attribute__((swift_name("dayOfWeek(names:)")));
- (void)monthNameNames:(FJKMPKotlinx_datetimeMonthNames *)names __attribute__((swift_name("monthName(names:)")));
- (void)monthNumberPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("monthNumber(padding:)")));
- (void)yearPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("year(padding:)")));
- (void)yearTwoDigitsBaseYear:(int32_t)baseYear __attribute__((swift_name("yearTwoDigits(baseYear:)")));
@end

__attribute__((swift_name("Kotlinx_datetimeDateTimeFormatBuilderWithTime")))
@protocol FJKMPKotlinx_datetimeDateTimeFormatBuilderWithTime <FJKMPKotlinx_datetimeDateTimeFormatBuilder>
@required
- (void)amPmHourPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("amPmHour(padding:)")));
- (void)amPmMarkerAm:(NSString *)am pm:(NSString *)pm __attribute__((swift_name("amPmMarker(am:pm:)")));
- (void)hourPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("hour(padding:)")));
- (void)minutePadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("minute(padding:)")));
- (void)secondPadding:(FJKMPKotlinx_datetimePadding *)padding __attribute__((swift_name("second(padding:)")));
- (void)secondFractionFixedLength:(int32_t)fixedLength __attribute__((swift_name("secondFraction(fixedLength:)")));
- (void)secondFractionMinLength:(int32_t)minLength maxLength:(int32_t)maxLength __attribute__((swift_name("secondFraction(minLength:maxLength:)")));
- (void)timeFormat:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("time(format:)")));
@end

__attribute__((swift_name("Kotlinx_datetimeDateTimeFormatBuilderWithDateTime")))
@protocol FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDateTime <FJKMPKotlinx_datetimeDateTimeFormatBuilderWithDate, FJKMPKotlinx_datetimeDateTimeFormatBuilderWithTime>
@required
- (void)dateTimeFormat:(id<FJKMPKotlinx_datetimeDateTimeFormat>)format __attribute__((swift_name("dateTime(format:)")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerializationStrategy")))
@protocol FJKMPKotlinx_serialization_coreSerializationStrategy
@required
- (void)serializeEncoder:(id<FJKMPKotlinx_serialization_coreEncoder>)encoder value:(id _Nullable)value __attribute__((swift_name("serialize(encoder:value:)")));
@property (readonly) id<FJKMPKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreDeserializationStrategy")))
@protocol FJKMPKotlinx_serialization_coreDeserializationStrategy
@required
- (id _Nullable)deserializeDecoder:(id<FJKMPKotlinx_serialization_coreDecoder>)decoder __attribute__((swift_name("deserialize(decoder:)")));
@property (readonly) id<FJKMPKotlinx_serialization_coreSerialDescriptor> descriptor __attribute__((swift_name("descriptor")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreKSerializer")))
@protocol FJKMPKotlinx_serialization_coreKSerializer <FJKMPKotlinx_serialization_coreSerializationStrategy, FJKMPKotlinx_serialization_coreDeserializationStrategy>
@required
@end

__attribute__((swift_name("KotlinByteIterator")))
@interface FJKMPKotlinByteIterator : FJKMPBase <FJKMPKotlinIterator>
- (instancetype)init __attribute__((swift_name("init()"))) __attribute__((objc_designated_initializer));
+ (instancetype)new __attribute__((availability(swift, unavailable, message="use object initializers instead")));
- (FJKMPByte *)next __attribute__((swift_name("next()")));
- (int8_t)nextByte __attribute__((swift_name("nextByte()")));
@end

__attribute__((swift_name("KotlinAppendable")))
@protocol FJKMPKotlinAppendable
@required
- (id<FJKMPKotlinAppendable>)appendValue:(unichar)value __attribute__((swift_name("append(value:)")));
- (id<FJKMPKotlinAppendable>)appendValue_:(id _Nullable)value __attribute__((swift_name("append(value_:)")));
- (id<FJKMPKotlinAppendable>)appendValue:(id _Nullable)value startIndex:(int32_t)startIndex endIndex:(int32_t)endIndex __attribute__((swift_name("append(value:startIndex:endIndex:)")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimePadding")))
@interface FJKMPKotlinx_datetimePadding : FJKMPKotlinEnum<FJKMPKotlinx_datetimePadding *>
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
- (instancetype)initWithName:(NSString *)name ordinal:(int32_t)ordinal __attribute__((swift_name("init(name:ordinal:)"))) __attribute__((objc_designated_initializer)) __attribute__((unavailable));
@property (class, readonly) FJKMPKotlinx_datetimePadding *none __attribute__((swift_name("none")));
@property (class, readonly) FJKMPKotlinx_datetimePadding *zero __attribute__((swift_name("zero")));
@property (class, readonly) FJKMPKotlinx_datetimePadding *space __attribute__((swift_name("space")));
+ (FJKMPKotlinArray<FJKMPKotlinx_datetimePadding *> *)values __attribute__((swift_name("values()")));
@property (class, readonly) NSArray<FJKMPKotlinx_datetimePadding *> *entries __attribute__((swift_name("entries")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeDayOfWeekNames")))
@interface FJKMPKotlinx_datetimeDayOfWeekNames : FJKMPBase
- (instancetype)initWithNames:(NSArray<NSString *> *)names __attribute__((swift_name("init(names:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithMonday:(NSString *)monday tuesday:(NSString *)tuesday wednesday:(NSString *)wednesday thursday:(NSString *)thursday friday:(NSString *)friday saturday:(NSString *)saturday sunday:(NSString *)sunday __attribute__((swift_name("init(monday:tuesday:wednesday:thursday:friday:saturday:sunday:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinx_datetimeDayOfWeekNamesCompanion *companion __attribute__((swift_name("companion")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSArray<NSString *> *names __attribute__((swift_name("names")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeMonthNames")))
@interface FJKMPKotlinx_datetimeMonthNames : FJKMPBase
- (instancetype)initWithNames:(NSArray<NSString *> *)names __attribute__((swift_name("init(names:)"))) __attribute__((objc_designated_initializer));
- (instancetype)initWithJanuary:(NSString *)january february:(NSString *)february march:(NSString *)march april:(NSString *)april may:(NSString *)may june:(NSString *)june july:(NSString *)july august:(NSString *)august september:(NSString *)september october:(NSString *)october november:(NSString *)november december:(NSString *)december __attribute__((swift_name("init(january:february:march:april:may:june:july:august:september:october:november:december:)"))) __attribute__((objc_designated_initializer));
@property (class, readonly, getter=companion) FJKMPKotlinx_datetimeMonthNamesCompanion *companion __attribute__((swift_name("companion")));
- (BOOL)isEqual:(id _Nullable)other __attribute__((swift_name("isEqual(_:)")));
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@property (readonly) NSArray<NSString *> *names __attribute__((swift_name("names")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreEncoder")))
@protocol FJKMPKotlinx_serialization_coreEncoder
@required
- (id<FJKMPKotlinx_serialization_coreCompositeEncoder>)beginCollectionDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor collectionSize:(int32_t)collectionSize __attribute__((swift_name("beginCollection(descriptor:collectionSize:)")));
- (id<FJKMPKotlinx_serialization_coreCompositeEncoder>)beginStructureDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (void)encodeBooleanValue:(BOOL)value __attribute__((swift_name("encodeBoolean(value:)")));
- (void)encodeByteValue:(int8_t)value __attribute__((swift_name("encodeByte(value:)")));
- (void)encodeCharValue:(unichar)value __attribute__((swift_name("encodeChar(value:)")));
- (void)encodeDoubleValue:(double)value __attribute__((swift_name("encodeDouble(value:)")));
- (void)encodeEnumEnumDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)enumDescriptor index:(int32_t)index __attribute__((swift_name("encodeEnum(enumDescriptor:index:)")));
- (void)encodeFloatValue:(float)value __attribute__((swift_name("encodeFloat(value:)")));
- (id<FJKMPKotlinx_serialization_coreEncoder>)encodeInlineDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("encodeInline(descriptor:)")));
- (void)encodeIntValue:(int32_t)value __attribute__((swift_name("encodeInt(value:)")));
- (void)encodeLongValue:(int64_t)value __attribute__((swift_name("encodeLong(value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNotNullMark __attribute__((swift_name("encodeNotNullMark()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNull __attribute__((swift_name("encodeNull()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNullableSerializableValueSerializer:(id<FJKMPKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableValue(serializer:value:)")));
- (void)encodeSerializableValueSerializer:(id<FJKMPKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableValue(serializer:value:)")));
- (void)encodeShortValue:(int16_t)value __attribute__((swift_name("encodeShort(value:)")));
- (void)encodeStringValue:(NSString *)value __attribute__((swift_name("encodeString(value:)")));
@property (readonly) FJKMPKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerialDescriptor")))
@protocol FJKMPKotlinx_serialization_coreSerialDescriptor
@required

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (NSArray<id<FJKMPKotlinAnnotation>> *)getElementAnnotationsIndex:(int32_t)index __attribute__((swift_name("getElementAnnotations(index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<FJKMPKotlinx_serialization_coreSerialDescriptor>)getElementDescriptorIndex:(int32_t)index __attribute__((swift_name("getElementDescriptor(index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (int32_t)getElementIndexName:(NSString *)name __attribute__((swift_name("getElementIndex(name:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (NSString *)getElementNameIndex:(int32_t)index __attribute__((swift_name("getElementName(index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)isElementOptionalIndex:(int32_t)index __attribute__((swift_name("isElementOptional(index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
@property (readonly) NSArray<id<FJKMPKotlinAnnotation>> *annotations __attribute__((swift_name("annotations")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
@property (readonly) int32_t elementsCount __attribute__((swift_name("elementsCount")));
@property (readonly) BOOL isInline __attribute__((swift_name("isInline")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
@property (readonly) BOOL isNullable __attribute__((swift_name("isNullable")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
@property (readonly) FJKMPKotlinx_serialization_coreSerialKind *kind __attribute__((swift_name("kind")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
@property (readonly) NSString *serialName __attribute__((swift_name("serialName")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreDecoder")))
@protocol FJKMPKotlinx_serialization_coreDecoder
@required
- (id<FJKMPKotlinx_serialization_coreCompositeDecoder>)beginStructureDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("beginStructure(descriptor:)")));
- (BOOL)decodeBoolean __attribute__((swift_name("decodeBoolean()")));
- (int8_t)decodeByte __attribute__((swift_name("decodeByte()")));
- (unichar)decodeChar __attribute__((swift_name("decodeChar()")));
- (double)decodeDouble __attribute__((swift_name("decodeDouble()")));
- (int32_t)decodeEnumEnumDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)enumDescriptor __attribute__((swift_name("decodeEnum(enumDescriptor:)")));
- (float)decodeFloat __attribute__((swift_name("decodeFloat()")));
- (id<FJKMPKotlinx_serialization_coreDecoder>)decodeInlineDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeInline(descriptor:)")));
- (int32_t)decodeInt __attribute__((swift_name("decodeInt()")));
- (int64_t)decodeLong __attribute__((swift_name("decodeLong()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)decodeNotNullMark __attribute__((swift_name("decodeNotNullMark()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (FJKMPKotlinNothing * _Nullable)decodeNull __attribute__((swift_name("decodeNull()")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id _Nullable)decodeNullableSerializableValueDeserializer:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeNullableSerializableValue(deserializer:)")));
- (id _Nullable)decodeSerializableValueDeserializer:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy>)deserializer __attribute__((swift_name("decodeSerializableValue(deserializer:)")));
- (int16_t)decodeShort __attribute__((swift_name("decodeShort()")));
- (NSString *)decodeString __attribute__((swift_name("decodeString()")));
@property (readonly) FJKMPKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeDayOfWeekNames.Companion")))
@interface FJKMPKotlinx_datetimeDayOfWeekNamesCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinx_datetimeDayOfWeekNamesCompanion *shared __attribute__((swift_name("shared")));
@property (readonly) FJKMPKotlinx_datetimeDayOfWeekNames *ENGLISH_ABBREVIATED __attribute__((swift_name("ENGLISH_ABBREVIATED")));
@property (readonly) FJKMPKotlinx_datetimeDayOfWeekNames *ENGLISH_FULL __attribute__((swift_name("ENGLISH_FULL")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("Kotlinx_datetimeMonthNames.Companion")))
@interface FJKMPKotlinx_datetimeMonthNamesCompanion : FJKMPBase
+ (instancetype)alloc __attribute__((unavailable));
+ (instancetype)allocWithZone:(struct _NSZone *)zone __attribute__((unavailable));
+ (instancetype)companion __attribute__((swift_name("init()")));
@property (class, readonly, getter=shared) FJKMPKotlinx_datetimeMonthNamesCompanion *shared __attribute__((swift_name("shared")));
@property (readonly) FJKMPKotlinx_datetimeMonthNames *ENGLISH_ABBREVIATED __attribute__((swift_name("ENGLISH_ABBREVIATED")));
@property (readonly) FJKMPKotlinx_datetimeMonthNames *ENGLISH_FULL __attribute__((swift_name("ENGLISH_FULL")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreCompositeEncoder")))
@protocol FJKMPKotlinx_serialization_coreCompositeEncoder
@required
- (void)encodeBooleanElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(BOOL)value __attribute__((swift_name("encodeBooleanElement(descriptor:index:value:)")));
- (void)encodeByteElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int8_t)value __attribute__((swift_name("encodeByteElement(descriptor:index:value:)")));
- (void)encodeCharElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(unichar)value __attribute__((swift_name("encodeCharElement(descriptor:index:value:)")));
- (void)encodeDoubleElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(double)value __attribute__((swift_name("encodeDoubleElement(descriptor:index:value:)")));
- (void)encodeFloatElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(float)value __attribute__((swift_name("encodeFloatElement(descriptor:index:value:)")));
- (id<FJKMPKotlinx_serialization_coreEncoder>)encodeInlineElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("encodeInlineElement(descriptor:index:)")));
- (void)encodeIntElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int32_t)value __attribute__((swift_name("encodeIntElement(descriptor:index:value:)")));
- (void)encodeLongElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int64_t)value __attribute__((swift_name("encodeLongElement(descriptor:index:value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)encodeNullableSerializableElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<FJKMPKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeNullableSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeSerializableElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index serializer:(id<FJKMPKotlinx_serialization_coreSerializationStrategy>)serializer value:(id _Nullable)value __attribute__((swift_name("encodeSerializableElement(descriptor:index:serializer:value:)")));
- (void)encodeShortElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(int16_t)value __attribute__((swift_name("encodeShortElement(descriptor:index:value:)")));
- (void)encodeStringElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index value:(NSString *)value __attribute__((swift_name("encodeStringElement(descriptor:index:value:)")));
- (void)endStructureDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)shouldEncodeElementDefaultDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("shouldEncodeElementDefault(descriptor:index:)")));
@property (readonly) FJKMPKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreSerializersModule")))
@interface FJKMPKotlinx_serialization_coreSerializersModule : FJKMPBase

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (void)dumpToCollector:(id<FJKMPKotlinx_serialization_coreSerializersModuleCollector>)collector __attribute__((swift_name("dumpTo(collector:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<FJKMPKotlinx_serialization_coreKSerializer> _Nullable)getContextualKClass:(id<FJKMPKotlinKClass>)kClass typeArgumentsSerializers:(NSArray<id<FJKMPKotlinx_serialization_coreKSerializer>> *)typeArgumentsSerializers __attribute__((swift_name("getContextual(kClass:typeArgumentsSerializers:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<FJKMPKotlinx_serialization_coreSerializationStrategy> _Nullable)getPolymorphicBaseClass:(id<FJKMPKotlinKClass>)baseClass value:(id)value __attribute__((swift_name("getPolymorphic(baseClass:value:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id<FJKMPKotlinx_serialization_coreDeserializationStrategy> _Nullable)getPolymorphicBaseClass:(id<FJKMPKotlinKClass>)baseClass serializedClassName:(NSString * _Nullable)serializedClassName __attribute__((swift_name("getPolymorphic(baseClass:serializedClassName:)")));
@end

__attribute__((swift_name("KotlinAnnotation")))
@protocol FJKMPKotlinAnnotation
@required
@end


/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
__attribute__((swift_name("Kotlinx_serialization_coreSerialKind")))
@interface FJKMPKotlinx_serialization_coreSerialKind : FJKMPBase
- (NSUInteger)hash __attribute__((swift_name("hash()")));
- (NSString *)description __attribute__((swift_name("description()")));
@end

__attribute__((swift_name("Kotlinx_serialization_coreCompositeDecoder")))
@protocol FJKMPKotlinx_serialization_coreCompositeDecoder
@required
- (BOOL)decodeBooleanElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeBooleanElement(descriptor:index:)")));
- (int8_t)decodeByteElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeByteElement(descriptor:index:)")));
- (unichar)decodeCharElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeCharElement(descriptor:index:)")));
- (int32_t)decodeCollectionSizeDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeCollectionSize(descriptor:)")));
- (double)decodeDoubleElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeDoubleElement(descriptor:index:)")));
- (int32_t)decodeElementIndexDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("decodeElementIndex(descriptor:)")));
- (float)decodeFloatElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeFloatElement(descriptor:index:)")));
- (id<FJKMPKotlinx_serialization_coreDecoder>)decodeInlineElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeInlineElement(descriptor:index:)")));
- (int32_t)decodeIntElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeIntElement(descriptor:index:)")));
- (int64_t)decodeLongElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeLongElement(descriptor:index:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (id _Nullable)decodeNullableSerializableElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeNullableSerializableElement(descriptor:index:deserializer:previousValue:)")));

/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
- (BOOL)decodeSequentially __attribute__((swift_name("decodeSequentially()")));
- (id _Nullable)decodeSerializableElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index deserializer:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy>)deserializer previousValue:(id _Nullable)previousValue __attribute__((swift_name("decodeSerializableElement(descriptor:index:deserializer:previousValue:)")));
- (int16_t)decodeShortElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeShortElement(descriptor:index:)")));
- (NSString *)decodeStringElementDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor index:(int32_t)index __attribute__((swift_name("decodeStringElement(descriptor:index:)")));
- (void)endStructureDescriptor:(id<FJKMPKotlinx_serialization_coreSerialDescriptor>)descriptor __attribute__((swift_name("endStructure(descriptor:)")));
@property (readonly) FJKMPKotlinx_serialization_coreSerializersModule *serializersModule __attribute__((swift_name("serializersModule")));
@end

__attribute__((objc_subclassing_restricted))
__attribute__((swift_name("KotlinNothing")))
@interface FJKMPKotlinNothing : FJKMPBase
@end


/**
 * @note annotations
 *   kotlinx.serialization.ExperimentalSerializationApi
*/
__attribute__((swift_name("Kotlinx_serialization_coreSerializersModuleCollector")))
@protocol FJKMPKotlinx_serialization_coreSerializersModuleCollector
@required
- (void)contextualKClass:(id<FJKMPKotlinKClass>)kClass provider:(id<FJKMPKotlinx_serialization_coreKSerializer> (^)(NSArray<id<FJKMPKotlinx_serialization_coreKSerializer>> *))provider __attribute__((swift_name("contextual(kClass:provider:)")));
- (void)contextualKClass:(id<FJKMPKotlinKClass>)kClass serializer:(id<FJKMPKotlinx_serialization_coreKSerializer>)serializer __attribute__((swift_name("contextual(kClass:serializer:)")));
- (void)polymorphicBaseClass:(id<FJKMPKotlinKClass>)baseClass actualClass:(id<FJKMPKotlinKClass>)actualClass actualSerializer:(id<FJKMPKotlinx_serialization_coreKSerializer>)actualSerializer __attribute__((swift_name("polymorphic(baseClass:actualClass:actualSerializer:)")));
- (void)polymorphicDefaultBaseClass:(id<FJKMPKotlinKClass>)baseClass defaultDeserializerProvider:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefault(baseClass:defaultDeserializerProvider:)"))) __attribute__((deprecated("Deprecated in favor of function with more precise name: polymorphicDefaultDeserializer")));
- (void)polymorphicDefaultDeserializerBaseClass:(id<FJKMPKotlinKClass>)baseClass defaultDeserializerProvider:(id<FJKMPKotlinx_serialization_coreDeserializationStrategy> _Nullable (^)(NSString * _Nullable))defaultDeserializerProvider __attribute__((swift_name("polymorphicDefaultDeserializer(baseClass:defaultDeserializerProvider:)")));
- (void)polymorphicDefaultSerializerBaseClass:(id<FJKMPKotlinKClass>)baseClass defaultSerializerProvider:(id<FJKMPKotlinx_serialization_coreSerializationStrategy> _Nullable (^)(id))defaultSerializerProvider __attribute__((swift_name("polymorphicDefaultSerializer(baseClass:defaultSerializerProvider:)")));
@end

__attribute__((swift_name("KotlinKDeclarationContainer")))
@protocol FJKMPKotlinKDeclarationContainer
@required
@end

__attribute__((swift_name("KotlinKAnnotatedElement")))
@protocol FJKMPKotlinKAnnotatedElement
@required
@end


/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
__attribute__((swift_name("KotlinKClassifier")))
@protocol FJKMPKotlinKClassifier
@required
@end

__attribute__((swift_name("KotlinKClass")))
@protocol FJKMPKotlinKClass <FJKMPKotlinKDeclarationContainer, FJKMPKotlinKAnnotatedElement, FJKMPKotlinKClassifier>
@required

/**
 * @note annotations
 *   kotlin.SinceKotlin(version="1.1")
*/
- (BOOL)isInstanceValue:(id _Nullable)value __attribute__((swift_name("isInstance(value:)")));
@property (readonly) NSString * _Nullable qualifiedName __attribute__((swift_name("qualifiedName")));
@property (readonly) NSString * _Nullable simpleName __attribute__((swift_name("simpleName")));
@end

#pragma pop_macro("_Nullable_result")
#pragma clang diagnostic pop
NS_ASSUME_NONNULL_END
