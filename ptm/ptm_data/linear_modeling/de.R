# Mike Lawrence's sensible interface to both aov and lme4
# This package loads lme4
library(ez)

# Baayen's language shit from his 2008 book
# We need this for pvals.fnc()
library(languageR)

# Load the source frame
d.src <- read.csv("source_frame.csv",header=T)
ezPrecis(d.src)

# Load the target frame
d.tgt <- read.csv("trans_frame.de.csv",header=T)
ezPrecis(d.tgt)

# User data
d.user <- read.csv("user_frame.csv",header=T)
ezPrecis(d.user)

# Merge these three frames
d.tmp1 <- merge(d.src,d.tgt,by.x = "src_id", by.y = "src_id")
de.data <- merge(d.tmp1,d.user,by.x = "user_id", by.y = "user_id")

# Lowess plots for the data
#
# Additional subjects: 53,54,57,60
#
# Outliers: 18,47 (in terms of time)
#
# Others: 3,4,5,6,12,13,14,15,25,28,35,36,42,46
#
de.data.graph <- de.data
de.data.graph$src_id <- as.factor(de.data.graph$src_id)
de.data.graph$user_id <- as.factor(de.data.graph$user_id)
xylowess.fnc(norm_time ~ src_id | user_id, data=de.data.graph, ylab = "norm time")

# Filter out the outliers for lowess plot
# Notin operator
`%ni%` <- Negate(`%in%`) 
de.data.graph <- de.data.graph[de.data.graph$user_id %ni% c(18,47),]
xylowess.fnc(norm_time ~ src_id | user_id, data=de.data.graph, ylab = "norm time")

#
# Iterate over subsets of users
#

# Users that we always want to include
perm_ids <- c(53,54,57,60)

# Users that we want to include in every model fitting iteration
user_ids <- combn(c(3,4,5,6,12,13,14,15,25,28,35,36,42,46), 4)

for (i in 1:ncol(user_ids)){ 

# User ids for model building
itr_users <- c(user_ids[,i],perm_ids)
print(itr_users)

test.subset <- subset(de.data, user_id %in% itr_users)
test.subset$src_id <- as.factor(test.subset$src_id)
test.subset$user_id <- as.factor(test.subset$user_id)

model.data <- subset(test.subset, select = c(norm_time,user_id,src_id,ui_id))

# Center the response so that the co-effecients are a bit easier to manage.
model.data$norm_time <- scale(model.data$norm_time,center=T)

#
# Specification of the linear model and p-values
#
# See D.Bates advice:
#
#   https://stat.ethz.ch/pipermail/r-sig-mixed-models/2009q3/002912.html
#
#   https://stat.ethz.ch/pipermail/r-help/2006-May/094765.html
#
de.A <- lmer(norm_time ~ ui_id + (1 + ui_id|user_id) + (1 + ui_id|src_id), data=model.data,REML=F)

print("MODEL FIT")
print(de.A)

# Model with no random effect
de.B <- lmer(norm_time ~ (1|user_id) + (1|src_id), data=model.data, REML=F)

# likelhihood ratio test
de.anova <- anova(de.A,de.B)

print("P-VALUES")
print(de.anova)

print("PARAMETERS")
print(ranef(de.A))
print(fixef(de.A))
}