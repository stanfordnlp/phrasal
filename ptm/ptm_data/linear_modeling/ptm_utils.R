#
# Misc. utils for working with ptm data
#


# x := name of the target translation frame
# returns: an R data frame
#
loadptmframe <- function(x){
  # Load the source frame
  d.src <- read.csv("source_frame.csv",header=T)

  # Load the target frame
  d.tgt <- read.csv(x,header=T)

  # User data
  d.user <- read.csv("user_frame.csv",header=T)

  # Merge these three frames
  d.tmp1 <- merge(d.src,d.tgt,by.x = "src_id", by.y = "src_id")
  my.data <- merge(d.tmp1,d.user,by.x = "user_id", by.y = "user_id")

  return(my.data)
}


#
# complement of the %in% operator
#
`%ni%` <- Negate(`%in%`)


#
# Gelman standardization 
#
# x := a vector to standardize
# returns: standardized vector
#
gscale <- function(x){
  y <- scale(x, center=T, scale=2*sd(x))
  return(y)
}


#
# Chi-square test
#
# x := chiquare value
# df := degrees of freedom
#
chisqtest <- function(x,df){
  return(1-pchisq(x,df))
}


#
# Fit mixed-effects model A
#
# x := data frame with the following columns
#
#   norm_time (response)
#   ui_id
#   user_id
#   src_id
#
#
library("lme4")
fitmix.A <- function(medata){
#
# Specification of the linear model and p-values. We will use the
# the log-likelihood test to obtain p-values.
#
# Note that for this test we fit with maximum likelihood (REML=F)
#
# See D.Bates advice:
#
#   https://stat.ethz.ch/pipermail/r-sig-mixed-models/2009q3/002912.html
#
#   https://stat.ethz.ch/pipermail/r-help/2006-May/094765.html
#

  model.A <- lmer(norm_time ~ ui_id + (1 + ui_id|user_id) + (1 + ui_id|src_id), data=medata, REML=F)

  print("MODEL FIT")
  print(model.A)

  # Reference model with no fixed effect
  model.ref <- lmer(norm_time ~ (1|user_id) + (1|src_id), data=medata, REML=F)

  # likelihood ratio test
  model.anova <- anova(model.A, model.ref)

  print("P-VALUES")
  print(model.anova)

  print("PARAMETERS")
  print(ranef(model.A))
  print(fixef(model.A))
}
