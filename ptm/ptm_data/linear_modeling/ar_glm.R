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
d.tgt <- read.csv("trans_frame.ar.csv",header=T)
ezPrecis(d.tgt)

# User data
d.user <- read.csv("user_frame.csv",header=T)
ezPrecis(d.user)

# Merge these three frames
d.tmp1 <- merge(d.src,d.tgt,by.x = "src_id", by.y = "src_id")
ar.data <- merge(d.tmp1,d.user,by.x = "user_id", by.y = "user_id")

# Lowess plots for the data
#
# not randomized: 10,11,16,22,23,24,33,34,40,43,45,49,50
#
# (must include these)
# randomized: 61,59,56,55
#
# Javascript broken: 48
#
# These are the outliers: 51 (in terms of time)
#
ar.data <- ar.data[ar.data$user_id %in% c(10,33,11,16,22,23,24,34,40,43,45,49,50,61,59,56,55),]
ar.data$src_id <- factor(ar.data$src_id, labels="s")
ar.data$user_id <- factor(ar.data$user_id,labels="u")

xylowess.fnc(norm_time ~ src_id | user_id, data=ar.data, ylab = "norm time")

# Center the response (dependent variable)
ar.data$norm_time <- scale(ar.data$norm_time, center=T)

#
# Pass a numeric column to do Gelman scaling
#
gscale <- function(x){
y <- scale(x, center=T, scale= 2*sd(x))
return(y)
}

# Gelman scaling of these numeric effects
ar.data$norm_syn_complexity <- gscale(ar.data$norm_syn_complexity)
ar.data$n_entity_tokens <- gscale(ar.data$n_entity_tokens)
ar.data$tgt_len <- gscale(ar.data$tgt_len)
ar.data$en_level <- gscale(ar.data$en_level)
ar.data$en_spell <- gscale(ar.data$en_spell)
ar.data$en_vocab <- gscale(ar.data$en_vocab)
ar.data$en_skills <- gscale(ar.data$en_skills)
ar.data$en_usage <- gscale(ar.data$en_usage)
ar.data$en_ar_trans <- gscale(ar.data$en_ar_trans)
ar.data$hours_per_week <- gscale(ar.data$hours_per_week)

# Fit the model
# tgt_len seems to be the only factor that makes any difference
ar.lm.A <- glm(norm_time ~ ui_id + user_id + src_id + tgt_len, data = ar.data) 

